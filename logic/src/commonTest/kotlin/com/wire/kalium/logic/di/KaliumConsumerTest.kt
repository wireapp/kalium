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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.logic.feature.message.ObserveMessageByIdUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KaliumConsumerTest {
    @Test
    fun assemblyDoesNotResolveUnrequestedUseCasesAndAClosedConsumerRejectsItsCallback() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val value = Any()
        assertSame(value, consumer.assemble(create = { value }, onDiscard = { error("Active assembly was discarded") }))
        consumer.closeAndJoin()
        assertFailsWith<CancellationException> {
            consumer.assemble(create = { error("Closed consumer admitted assembly") }, onDiscard = { error("No value exists") })
        }
        assertTrue(dependencies.messages.isEmpty())
        assertTrue(dependencies.conversations.isEmpty())
        registry.invalidateAndJoin()
    }

    @Test
    fun invalidationDuringAssemblyDiscardsTheValueAndDrainsItsJobWithoutCancellingTheParent() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val parent = Job()
        val owner = SupervisorJob(parent)
        val finishCleanup = CompletableDeferred<Unit>()
        var unwound = false
        var discards = 0
        val ownedWork = launch(owner, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    finishCleanup.await()
                    unwound = true
                }
            }
        }
        val value = Any()
        val discardFailure = IllegalStateException("discard failed after cancellation")

        val failure = assertFailsWith<CancellationException> {
            consumer.assemble(
                create = { registry.invalidate(); value },
                onDiscard = { assertSame(value, it); discards++; throw discardFailure },
                jobOnDiscard = { owner },
            )
        }
        assertEquals(listOf(discardFailure), failure.suppressedExceptions)
        val drain = async { registry.invalidateAndJoin() }
        runCurrent()
        assertEquals(1, discards)
        assertTrue(owner.isCancelled)
        assertFalse(drain.isCompleted)
        assertFalse(unwound)
        assertTrue(parent.isActive)
        assertEquals(0, dependencies.resources.closes)
        finishCleanup.complete(Unit)
        drain.await()
        ownedWork.join()
        assertTrue(unwound)
        assertTrue(parent.isActive)
        parent.cancel()
    }

    @Test
    fun acceptedAssemblyLeavesCreatorAndParentJobsOutsideConsumerDisposal() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val parent = Job()
        val owner = SupervisorJob(parent)
        val creatorWork = launch(owner, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { awaitCancellation() }
        try {
            consumer.assemble(create = { owner }, onDiscard = { error("Active assembly was discarded") }, jobOnDiscard = { it })
            consumer.closeAndJoin()
            registry.invalidateAndJoin()
            assertTrue(owner.isActive)
            assertTrue(creatorWork.isActive)
            assertTrue(parent.isActive)
            assertEquals(0, dependencies.resources.closes)
        } finally {
            parent.cancel()
            creatorWork.join()
        }
    }

    @Test
    fun nonCancellableTerminalCreatorDrainsConsumerReadsAndContinuesWithoutWaitingForItself() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val owner = SupervisorJob()
        val readEntered = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        var unwound = false
        var continued = false
        dependencies.read = {
            readEntered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    finishCleanup.await()
                    unwound = true
                }
            }
        }
        consumer.assemble(create = { owner }, onDiscard = {}, jobOnDiscard = { it })
        val reading = launch(owner) { consumer.getMessageById(TestConversation.ID, "message") }
        readEntered.await()
        val terminal = launch(owner) {
            withContext(NonCancellable) {
                registry.invalidateAndJoin()
                continued = true
            }
        }
        try {
            cleanupEntered.await()
            assertFalse(terminal.isCompleted)
            assertTrue(owner.isActive)
            assertFalse(unwound)
            finishCleanup.complete(Unit)
            runCurrent()
            assertTrue(terminal.isCompleted)
            assertFalse(terminal.isCancelled)
            assertTrue(continued)
            assertTrue(unwound)
            assertTrue(reading.isCancelled)
            assertTrue(owner.isActive)
            assertFailsWith<CancellationException> { consumer.getMessageById(TestConversation.ID, "message") }
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishCleanup.complete(Unit)
            owner.cancel()
        }
    }

    @Test
    fun failedAssemblyReleasesAdmissionAndPreservesConstructionAndDiscardFailures() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val constructionFailure = IllegalArgumentException("assembly failed")
        val thrown = assertFailsWith<IllegalArgumentException> {
            consumer.assemble(create = { throw constructionFailure }, onDiscard = { error("No value exists") })
        }
        assertSame(constructionFailure, thrown)
        consumer.closeAndJoin()

        val ownerFailure = IllegalArgumentException("owner lookup failed")
        val ownerLookup = registry.createConsumer()
        val value = Any()
        var discards = 0
        assertSame(ownerFailure, assertFailsWith<IllegalArgumentException> {
            ownerLookup.assemble(
                create = { value },
                onDiscard = { assertSame(value, it); discards++ },
                jobOnDiscard = { throw ownerFailure },
            )
        })
        assertEquals(1, discards)
        ownerLookup.closeAndJoin()

        val other = registry.createConsumer()
        val cleanupFailure = IllegalStateException("discard failed")
        val cancelled = assertFailsWith<CancellationException> {
            other.assemble(create = { registry.invalidate(); Any() }, onDiscard = { throw cleanupFailure })
        }
        assertEquals(listOf(cleanupFailure), cancelled.suppressedExceptions)
        registry.invalidateAndJoin()
        assertEquals(0, dependencies.resources.closes)
    }

    @Test
    fun invalidatingAnUnusedRegistryDoesNotResolveTheGraphOrResources() = runTest {
        var creations = 0
        val registry = KaliumConsumerRegistry {
            creations++
            error("An unused graph must stay unresolved")
        }
        registry.invalidate()
        registry.invalidateAndJoin()
        assertEquals(0, creations)
        assertFailsWith<IllegalStateException> { registry.createConsumer() }
        assertEquals(0, creations)
    }

    @Test
    fun sameConsumerSharesRepositoriesAcrossFreshUseCasesAndOtherConsumersDoNot() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val first = registry.createConsumer()
        val second = registry.createConsumer()
        assertTrue(dependencies.messages.isEmpty())
        assertTrue(dependencies.conversations.isEmpty())

        val get = first.getMessageById
        val observe = first.observeMessageById
        assertNotSame(get, first.getMessageById)
        assertNotSame(observe, first.observeMessageById)
        assertNotSame(first.observeConversationDetails, first.observeConversationDetails)
        assertEquals(1, dependencies.messages.size)
        assertEquals(1, dependencies.conversations.size)

        val got = get(TestConversation.ID, "message") as GetMessageByIdUseCase.Result.Success
        val observed = observe(TestConversation.ID, "message").first() as ObserveMessageByIdUseCase.Result.Success
        assertSame(got.message, observed.message)
        assertEquals(1, dependencies.messages.single().reads)
        assertEquals(1, dependencies.messages.single().observations)

        second.getMessageById(TestConversation.ID, "message")
        second.observeConversationDetails(TestConversation.ID).first()
        assertEquals(2, dependencies.messages.size)
        assertEquals(2, dependencies.conversations.size)
        assertNotSame(dependencies.messages[0], dependencies.messages[1])
        assertNotSame(dependencies.conversations[0], dependencies.conversations[1])
        assertSame(dependencies.resources, dependencies.messages[0].resources)
        assertSame(dependencies.messages[0].resources, dependencies.messages[1].resources)
        assertSame(dependencies.messages[0].resources, dependencies.conversations[1].resources)

        first.closeAndJoin()
        assertEquals(0, dependencies.resources.closes)
        assertTrue(second.getMessageById(TestConversation.ID, "message") is GetMessageByIdUseCase.Result.Success)
        registry.invalidateAndJoin()
        assertEquals(0, dependencies.resources.closes)
        assertFailsWith<IllegalStateException> { registry.createConsumer() }
    }

    @Test
    fun heldUseCasesAndColdFlowsCannotAccessResourcesAfterClose() = runTest {
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val get = consumer.getMessageById
        val observe = consumer.observeMessageById
        val conversation = consumer.observeConversationDetails
        val messageFlow = observe(TestConversation.ID, "message")
        val conversationFlow = conversation(TestConversation.ID)

        consumer.closeAndJoin()
        consumer.closeAndJoin()
        assertFailsWith<CancellationException> { consumer.getMessageById }
        assertFailsWith<CancellationException> { get(TestConversation.ID, "message") }
        assertFailsWith<CancellationException> { observe(TestConversation.ID, "message") }
        assertFailsWith<CancellationException> { conversation(TestConversation.ID) }
        assertFailsWith<CancellationException> { messageFlow.first() }
        assertFailsWith<CancellationException> { conversationFlow.first() }
        assertEquals(0, dependencies.messages.single().reads)
        assertEquals(0, dependencies.messages.single().observations)
        assertEquals(0, dependencies.conversations.single().observations)
        registry.invalidateAndJoin()
    }

    @Test
    fun closeCancelsCallerHeldOperationAndDrainsItsNonCancellableCleanup() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cleanupEntered = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        dependencies.read = {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    finishCleanup.await()
                }
            }
        }
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val held = consumer.getMessageById
        val hostJob = Job()
        val operation = launch(hostJob) { held(TestConversation.ID, "message") }
        entered.await()
        val drain = async { consumer.closeAndJoin() }
        cleanupEntered.await()
        runCurrent()
        assertFalse(drain.isCompleted)
        assertTrue(hostJob.isActive)
        assertEquals(0, dependencies.resources.closes)
        finishCleanup.complete(Unit)
        drain.await()
        operation.join()
        assertTrue(operation.isCancelled)
        assertTrue(hostJob.isActive)
        hostJob.cancel()
        registry.invalidateAndJoin()
    }

    @Test
    fun invalidateCancelsMessageAndConversationCollectorsInCallerContextAndWaitsForBoth() = runTest {
        val messageEntered = CompletableDeferred<Unit>()
        val conversationEntered = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        var cleanups = 0
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        dependencies.messageFlow = {
            flow {
                messageEntered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) {
                        finishCleanup.await()
                        cleanups++
                    }
                }
            }
        }
        dependencies.conversationFlow = {
            flow {
                conversationEntered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) {
                        finishCleanup.await()
                        cleanups++
                    }
                }
            }
        }
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        // These flows escape the consumer and are collected in independent caller jobs.
        val messageFlow = consumer.observeMessageById(TestConversation.ID, "message")
        val conversationFlow = consumer.observeConversationDetails(TestConversation.ID)
        val messageCollector = launch { messageFlow.collect() }
        val conversationCollector = launch { conversationFlow.collect() }
        messageEntered.await()
        conversationEntered.await()
        consumer.close() // Registry must retain this closing consumer until it drains.
        val drain = async { registry.invalidateAndJoin() }
        runCurrent()
        assertFalse(drain.isCompleted)
        assertFailsWith<IllegalStateException> { registry.createConsumer() }
        finishCleanup.complete(Unit)
        drain.await()
        messageCollector.join()
        conversationCollector.join()
        assertEquals(2, cleanups)
        assertTrue(messageCollector.isCancelled)
        assertTrue(conversationCollector.isCancelled)
        assertEquals(0, dependencies.resources.closes)
    }

    @Test
    fun callerCancellationUnwindsOwnedWorkWithoutClosingTheConsumer() = runTest {
        val entered = CompletableDeferred<Unit>()
        var unwound = false
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        dependencies.read = {
            entered.complete(Unit)
            try { awaitCancellation() } finally { unwound = true }
        }
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val operation = launch { consumer.getMessageById(TestConversation.ID, "message") }
        entered.await()
        operation.cancel()
        operation.join()
        assertTrue(unwound)
        dependencies.read = { Either.Right(dependencies.resources.message) }
        consumer.getMessageById(TestConversation.ID, "message")
        registry.invalidateAndJoin()
    }

    @Test
    fun failedConsumerCreationCancelsOnlyItsWorkAndDoesNotPoisonTheRegistry() = runTest {
        val entered = CompletableDeferred<Unit>()
        var unwound = false
        val dependencies = ConsumerTestDependencies(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val realGraph = dependencies.userGraph()
        var failing = true
        var failedOperation: Job? = null
        val hostJob = Job()
        val registry = KaliumConsumerRegistry { work ->
            if (failing) {
                failedOperation = launch(hostJob, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    work.execute {
                        entered.complete(Unit)
                        try { awaitCancellation() } finally { unwound = true }
                    }
                }
                error("graph creation failed")
            }
            realGraph.consumerGraphFactory.create(work)
        }
        assertFailsWith<IllegalStateException> { registry.createConsumer() }
        entered.await()
        failedOperation!!.join()
        assertTrue(unwound)
        assertTrue(hostJob.isActive)
        assertEquals(0, dependencies.resources.closes)
        failing = false
        val healthy = registry.createConsumer()
        healthy.getMessageById(TestConversation.ID, "message")
        registry.invalidateAndJoin()
        hostJob.cancel()
    }

    @Test
    fun aNewRegistryCanCreateConsumersAfterThePreviousGenerationIsInvalidated() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        val firstDependencies = ConsumerTestDependencies(dispatcher)
        val firstRegistry = KaliumConsumerRegistry(firstDependencies.userGraph())
        val old = firstRegistry.createConsumer().getMessageById
        firstRegistry.invalidateAndJoin()
        val secondDependencies = ConsumerTestDependencies(dispatcher)
        val secondRegistry = KaliumConsumerRegistry(secondDependencies.userGraph())
        val current = secondRegistry.createConsumer()
        current.getMessageById(TestConversation.ID, "message")
        assertFailsWith<CancellationException> { old(TestConversation.ID, "message") }
        assertNotSame(firstDependencies.resources, secondDependencies.resources)
        secondRegistry.invalidateAndJoin()
    }
}

internal class ConsumerTestResources : AutoCloseable {
    val message = TestMessage.TEXT_MESSAGE
    var closes = 0
    override fun close() { closes++ }
}

internal class ConsumerTestDependencies(override val dispatchers: KaliumDispatcher) : UserGraphDependencies {
    val resources = ConsumerTestResources()
    val messages = mutableListOf<ConsumerTestMessageRepository>()
    val conversations = mutableListOf<ConsumerTestConversationRepository>()
    var read: suspend () -> Either<StorageFailure, Message> = { Either.Right(resources.message) }
    var messageFlow: () -> Flow<Either<StorageFailure, Message>> = { flowOf(Either.Right(resources.message)) }
    var conversationFlow: () -> Flow<Either<StorageFailure, ConversationDetails>> = {
        flowOf(Either.Left(StorageFailure.DataNotFound))
    }

    override fun createMessageRepository(): MessageRepository =
        ConsumerTestMessageRepository(resources, { read() }, { messageFlow() }).also { messages.add(it) }

    override fun createConversationRepository(): ConversationRepository =
        ConsumerTestConversationRepository(resources) { conversationFlow() }.also { conversations.add(it) }

    fun userGraph(): KaliumUserGraph = createKaliumGraph().userGraphFactory.create(
        this,
        mock<LegacyConversationDependencies>(),
        mock<LegacyMessageDependencies>(),
    )
}

internal class ConsumerTestMessageRepository(
    val resources: ConsumerTestResources,
    private val read: suspend () -> Either<StorageFailure, Message>,
    private val observe: () -> Flow<Either<StorageFailure, Message>>,
) : MessageRepository by mock<MessageRepository>() {
    var reads = 0
    var observations = 0
    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message> {
        reads++
        return read()
    }
    override fun observeMessageById(conversationId: ConversationId, messageUuid: String): Flow<Either<StorageFailure, Message>> {
        observations++
        return observe()
    }
}

internal class ConsumerTestConversationRepository(
    val resources: ConsumerTestResources,
    private val observe: () -> Flow<Either<StorageFailure, ConversationDetails>>,
) : ConversationRepository by mock<ConversationRepository>() {
    var observations = 0
    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>> {
        observations++
        return observe()
    }
}
