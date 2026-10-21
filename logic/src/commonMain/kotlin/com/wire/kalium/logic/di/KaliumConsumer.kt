/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.di

import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.ObserveMessageByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Explicit owner for the selected use cases. Each getter returns a fresh use case; repositories are
 * shared only inside this consumer. Held use cases and flows reject resource work after closure.
 *
 * [close] stops admission and cancels owned operations/collections without blocking. [closeAndJoin]
 * also awaits their unwind. Neither closes user storage, network, crypto or user coroutine jobs.
 */
public interface KaliumConsumer : AutoCloseable {
    public val observeConversationDetails: ObserveConversationDetailsUseCase
    public val observeMessageById: ObserveMessageByIdUseCase
    public val getMessageById: GetMessageByIdUseCase

    /**
     * Admits the creator's actual synchronous construction, including work after consumer creation.
     * Returns only if this consumer is still active when construction finishes. Otherwise [onDiscard]
     * releases the unpublished value before construction is considered drained. If [create] throws,
     * it is responsible for any partially constructed value; the caller must close this consumer.
     *
     * The published creator owns this consumer, not the other way round. Its UI/global/terminal work
     * is not consumer-owned: logout may await consumer drain from that creator's coroutine scope.
     * Resource work through the guarded use cases is admitted and drained independently.
     *
     * [jobOnDiscard] looks up only the constructed value's own job, never a user/global or caller job.
     * The lookup runs before publication, but the job is retained, cancelled and joined ONLY if the
     * value is rejected. This includes non-cancellable children even if [onDiscard] throws. An accepted
     * value's job is never retained or cancelled by this consumer. [onDiscard] must release only the
     * unpublished value's resources and is also called if [jobOnDiscard] throws.
     */
    public fun <T> assemble(
        create: () -> T,
        onDiscard: (T) -> Unit,
        jobOnDiscard: (T) -> Job? = { null },
    ): T

    public override fun close()
    public suspend fun closeAndJoin()
}

/**
 * One registry per prepared user generation. The host invalidates and drains this registry before
 * account shutdown, and must separately quiesce escaped LEGACY operations and observers. Suspension
 * on Apple does not invalidate the generation. No lazy resource is resolved for disposal.
 */
internal class KaliumConsumerRegistry(
    private val createGraph: (ConsumerWork) -> KaliumConsumerGraph,
) {
    private val lock = Lock()
    private var active = true

    // Keep closed consumers until their admitted work (including synchronous construction) drains.
    private val consumers = mutableSetOf<ConsumerWork>()

    @Suppress("TooGenericExceptionCaught") // Every failed construction must close its admitted work.
    fun createConsumer(): KaliumConsumer {
        val work = ConsumerWork(
            isGenerationActive = { lock.withLock { active } },
            onDrained = { drained -> lock.withLock { consumers.remove(drained) } },
        )
        lock.withLock {
            check(active) { "The user graph generation has been invalidated" }
            consumers.add(work)
        }
        return try {
            val consumer = work.resolve { OwnedKaliumConsumer(createGraph(work), work) }
            lock.withLock {
                check(active) { "The user graph generation was invalidated during consumer creation" }
                consumer
            }
        } catch (failure: Throwable) {
            work.close()
            throw failure
        }
    }

    /** Prevents publication of new consumers, including in-flight creations, then cancels owned work. */
    fun invalidateConsumers() {
        outgoingConsumers().forEach { it.close() }
    }

    /** Must complete successfully before the host closes account resources. Safe to retry if cancelled. */
    suspend fun drainConsumers() {
        val outgoing = outgoingConsumers()
        outgoing.forEach { it.close() }
        outgoing.forEach { it.awaitClosed() }
    }

    private fun outgoingConsumers(): List<ConsumerWork> = lock.withLock {
        active = false
        consumers.toList()
    }
}

private class OwnedKaliumConsumer(
    private val graph: KaliumConsumerGraph,
    private val work: ConsumerWork,
) : KaliumConsumer {
    override val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = work.resolve { graph.observeConversationDetails }
    override val observeMessageById: ObserveMessageByIdUseCase
        get() = work.resolve { graph.observeMessageById }
    override val getMessageById: GetMessageByIdUseCase
        get() = work.resolve { graph.getMessageById }

    override fun <T> assemble(create: () -> T, onDiscard: (T) -> Unit, jobOnDiscard: (T) -> Job?): T =
        work.assemble(create, onDiscard, jobOnDiscard)

    override fun close() = work.close()

    override suspend fun closeAndJoin() {
        close()
        work.awaitClosed()
    }
}

/**
 * Admission and explicit operation ownership, independent of the caller's coroutine scope. Cancelling
 * a graph job alone would miss use cases and cold flows collected in an arbitrary caller context.
 * Jobs are children of the caller (so caller cancellation still works), and are also tracked here.
 * Drain counts actual finally/unwind, rather than Job cancellation, including non-cancellable cleanup.
 */
internal class ConsumerWork(
    private val isGenerationActive: () -> Boolean = { true },
    private val onDrained: (ConsumerWork) -> Unit = {},
    private val revocation: CancellationException? = null,
) {
    private val lock = Lock()
    private var open = true
    private val operations = mutableSetOf<CompletableJob>()
    private val discardedJobs = mutableSetOf<Job>()
    private val drained = CompletableDeferred<Unit>()

    fun <T> resolve(block: () -> T): T {
        val operation = admit()
        return try {
            val value = block()
            checkOpen()
            value
        } finally {
            release(operation)
        }
    }

    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth") // Preserve the original failure even if discard also fails.
    fun <T> assemble(create: () -> T, onDiscard: (T) -> Unit, jobOnDiscard: (T) -> Job?): T {
        val operation = admit()
        try {
            val value = create()
            var unpublishedJob: Job? = null
            try {
                unpublishedJob = jobOnDiscard(value)
                checkOpen()
                return value
            } catch (failure: Throwable) {
                // Keep assembly admitted until discard and job registration finish. Once published,
                // the creator keeps its own job; retaining it here would make terminal logout self-wait.
                try {
                    unpublishedJob?.let(::drainDiscardedJob)
                } catch (cleanupFailure: Throwable) {
                    if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                }
                try {
                    onDiscard(value)
                } catch (cleanupFailure: Throwable) {
                    if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        } finally {
            release(operation)
        }
    }

    suspend fun <T> execute(block: suspend () -> T): T {
        val operation = admit(currentCoroutineContext()[Job])
        return try {
            withContext(operation) { block() }
        } finally {
            release(operation)
        }
    }

    fun <T> flow(source: suspend () -> Flow<T>): Flow<T> {
        checkOpen()
        // The resource flow is created and collected inside an admitted operation on every collection.
        // channelFlow preserves Flow's context invariant while allowing the owned operation job.
        return channelFlow { execute { source().collect { send(it) } } }.buffer(0)
    }

    fun close() {
        val outgoing = lock.withLock {
            open = false
            operations.toList() + discardedJobs.toList()
        }
        outgoing.forEach { it.cancel(revocation ?: CancellationException("Kalium consumer closed")) }
        completeIfDrained()
    }

    suspend fun awaitClosed() {
        drained.await()
    }

    private fun checkOpen() = lock.withLock {
        if (!open) throw (revocation ?: CancellationException("Kalium consumer closed"))
        // Invalidation closes the generation before iterating consumers. An earlier consumer's
        // cancellation may still be unwinding, so checking only this consumer's open flag is late.
        if (!isGenerationActive()) throw (revocation ?: CancellationException("The user graph generation has been invalidated"))
    }

    private fun admit(parent: Job? = null): CompletableJob = lock.withLock {
        checkOpen()
        Job(parent).also { operations.add(it) }
    }

    private fun release(operation: CompletableJob) {
        operation.complete()
        lock.withLock { operations.remove(operation) }
        completeIfDrained()
    }

    private fun drainDiscardedJob(job: Job) {
        lock.withLock { discardedJobs.add(job) }
        job.invokeOnCompletion {
            lock.withLock { discardedJobs.remove(job) }
            completeIfDrained()
        }
        job.cancel(CancellationException("Kalium consumer rejected construction"))
    }

    private fun completeIfDrained() {
        val complete = lock.withLock { !open && operations.isEmpty() && discardedJobs.isEmpty() }
        if (complete && drained.complete(Unit)) onDrained(this)
    }
}

/** Only the pilot methods are exposed through consumer use cases. Extend the guards with each slice. */
internal class ConsumerMessageRepository(
    private val delegate: MessageRepository,
    private val work: ConsumerWork,
) : MessageRepository by delegate {
    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message> =
        work.execute { delegate.getMessageById(conversationId, messageUuid) }

    override fun observeMessageById(conversationId: ConversationId, messageUuid: String): Flow<Either<StorageFailure, Message>> =
        work.flow { delegate.observeMessageById(conversationId, messageUuid) }
}

internal class ConsumerConversationRepository(
    private val delegate: ConversationRepository,
    private val work: ConsumerWork,
) : ConversationRepository by delegate {
    override suspend fun observeConversationDetailsById(
        conversationID: ConversationId,
    ): Flow<Either<StorageFailure, ConversationDetails>> = work.flow {
        delegate.observeConversationDetailsById(conversationID)
    }
}
