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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiver.conversation.message.DurableMLSReceiveContext
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Applies one gathered app event batch and completes its queue acknowledgement.
 *
 * The batch owns cancellation until native transactions and acknowledgement finish.
 * Required effect and receipt failures throw from the native callback to prevent a commit.
 * Queue acknowledgement starts only after both native transactions return successfully.
 * Standard and durable receiving retain their own traversal and acknowledgement failure policy.
 */
internal interface EventBatchProcessor {
    suspend fun processBatch(envelopes: List<EventEnvelope>)

    companion object {
        fun standard(
            eventProcessor: EventProcessor,
            transactionProvider: CryptoTransactionProvider,
            databaseBuilder: UserDatabaseBuilder,
            eventRepository: EventRepository,
            logger: KaliumLogger = kaliumLogger,
        ): EventBatchProcessor = EventBatchProcessorImpl(
            eventProcessor,
            transactionProvider,
            databaseBuilder,
            StandardEventBatchReceiver(eventProcessor, eventRepository, logger.withFeatureId(SYNC)),
            logger,
        )

        fun durable(
            eventProcessor: EventProcessor,
            transactionProvider: CryptoTransactionProvider,
            databaseBuilder: UserDatabaseBuilder,
            recovery: suspend () -> EventBatchRecovery,
            logger: KaliumLogger = kaliumLogger,
        ): EventBatchProcessor = EventBatchProcessorImpl(
            eventProcessor,
            transactionProvider,
            databaseBuilder,
            DurableEventBatchReceiver(eventProcessor, recovery, logger.withFeatureId(SYNC)),
            logger,
        )
    }
}

/** Durable evidence and queue completion behind the batch module's internal recovery seam. */
internal interface EventBatchRecovery {
    suspend fun wereEffectsProcessed(context: CryptoTransactionContext, envelope: EventEnvelope): Boolean
    suspend fun recordProcessed(context: CryptoTransactionContext, events: List<EventEnvelope>)
    suspend fun acknowledge(eventIds: List<String>)
}

private class EventBatchProcessorImpl(
    private val eventProcessor: EventProcessor,
    private val transactionProvider: CryptoTransactionProvider,
    private val databaseBuilder: UserDatabaseBuilder,
    private val receiver: EventBatchReceiver,
    logger: KaliumLogger,
) : EventBatchProcessor {
    private val logger = logger.withFeatureId(SYNC)

    override suspend fun processBatch(envelopes: List<EventEnvelope>): Unit = withContext(NonCancellable) {
        transactionProvider.transaction("processEvents") { transactionContext ->
            val context = receiver.context(transactionContext)
            databaseBuilder.dbInvalidationController.runMuted {
                receiver.process(context, envelopes)
                    .flatMap { batch ->
                        eventProcessor.flushPendingSideEffects().map {
                            receiver.recordProcessed(context, batch.envelopes)
                            batch.eventIds
                        }
                    }
                    // Returning Either.Left would still let native crypto commit.
                    .onFailure { throw KaliumSyncException("Processing failed", it) }
            }
        }.onSuccess { eventIds ->
            if (eventIds.isEmpty()) logger.i("No events to mark as processed")
            else receiver.acknowledge(eventIds)
        }.onFailure { throw KaliumSyncException("Processing failed", it) }
        Unit
    }
}

/** Private to the batch implementation: callers cannot configure half of the recovery contract. */
private interface EventBatchReceiver {
    fun context(context: CryptoTransactionContext): CryptoTransactionContext = context
    suspend fun process(context: CryptoTransactionContext, envelopes: List<EventEnvelope>): Either<CoreFailure, ProcessedBatch>
    suspend fun recordProcessed(context: CryptoTransactionContext, envelopes: List<EventEnvelope>)
    suspend fun acknowledge(eventIds: List<String>)
}

private data class ProcessedBatch(val eventIds: List<String>, val envelopes: List<EventEnvelope> = emptyList())

private class StandardEventBatchReceiver(
    private val eventProcessor: EventProcessor,
    private val eventRepository: EventRepository,
    private val logger: KaliumLogger,
) : EventBatchReceiver {
    override suspend fun process(
        context: CryptoTransactionContext,
        envelopes: List<EventEnvelope>,
    ): Either<CoreFailure, ProcessedBatch> =
        // Preserve standard receiving: process the entire batch before collecting failures.
        envelopes.map { eventProcessor.processEvent(context, it) }
            .foldToEitherWhileRight(mutableListOf<String>()) { result, eventIds ->
                result.map { eventId ->
                    eventId?.let(eventIds::add)
                    eventIds
                }
            }.map { ProcessedBatch(it) }

    override suspend fun recordProcessed(context: CryptoTransactionContext, envelopes: List<EventEnvelope>) = Unit

    override suspend fun acknowledge(eventIds: List<String>) {
        // Standard marker failure does not stop the next gathered batch.
        eventRepository.setEventsAsProcessed(eventIds)
            .onSuccess { logger.i("${eventIds.size} events set as processed") }
    }
}

private class DurableEventBatchReceiver(
    private val eventProcessor: EventProcessor,
    private val recovery: suspend () -> EventBatchRecovery,
    private val logger: KaliumLogger,
) : EventBatchReceiver {
    override fun context(context: CryptoTransactionContext): CryptoTransactionContext = DurableMLSReceiveContext(context)

    override suspend fun process(
        context: CryptoTransactionContext,
        envelopes: List<EventEnvelope>,
    ): Either<CoreFailure, ProcessedBatch> {
        val processed = mutableListOf<EventEnvelope>()
        return envelopes.foldToEitherWhileRight(mutableListOf<String>()) { envelope, eventIds ->
            processEnvelope(context, envelope).map { eventId ->
                if (eventId != null) {
                    processed += envelope
                    eventIds += envelope.payloadIdentity?.eventId ?: eventId
                }
                eventIds
            }
        }.map { ProcessedBatch(it, processed) }
    }

    private suspend fun processEnvelope(
        context: CryptoTransactionContext,
        envelope: EventEnvelope,
    ): Either<CoreFailure, String?> {
        val effectsProcessed = !eventProcessor.disableEventProcessing && envelope.event is Event.Conversation &&
            recovery().wereEffectsProcessed(context, envelope)
        // A conversation receipt cannot prove that another scope completed deferred work.
        // Connections and other event families must keep their normal replay.
        if (effectsProcessed && envelope.event !is Event.Conversation.NewMessage) return Either.Right(envelope.event.id)
        // Repair an outer Proteus rollback by decrypting again, without overwriting canonical effects.
        val eventContext = if (effectsProcessed) DurableMLSReceiveContext(context, effectsAlreadyPersisted = true) else context
        return eventProcessor.processEvent(eventContext, envelope)
    }

    override suspend fun recordProcessed(context: CryptoTransactionContext, envelopes: List<EventEnvelope>) {
        recovery().recordProcessed(context, envelopes)
    }

    override suspend fun acknowledge(eventIds: List<String>) {
        // The Apple store atomically marks queue rows and retires recovery receipts.
        // Failure throws, retaining replay inputs and stopping the next gathered batch.
        recovery().acknowledge(eventIds)
        logger.i("${eventIds.size} events set as processed")
    }
}
