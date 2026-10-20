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
package com.wire.kalium.logic.feature

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.UserSessionRuntime
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCase
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.network.ApiMigration
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncRequest
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.deferred.MainAppPendingDeliveryWorker
import com.wire.kalium.logic.sync.local.LocalEventManager
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationDAO
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationEntity
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionRuntimeTest {

    @Test
    fun startRunsAllMandatoryWorkersOnceIncludingRepeatedCallsBeforeAndAfterDispatch() = runTest {
        withFixture {
            clientReady.complete(Unit)
            syncLive.complete(Unit)
            assertTrue(resolutions.isEmpty())

            runtime.start()
            runtime.start()
            runCurrent()
            runtime.start()
            runCurrent()

            // These are worker calls and real workers' port calls, rather than factory reads.
            val expectedWorkers = listOf(
                "initial-sync", "pending-proposals", "nomad-hooks", "api-migration", "asset-reset", "stale-call-cleanup",
                "push-tokens", "mls-recovery", "join-pending-mls", "conversation-recovery", "self-deletion",
                "e2ei", "typing", "repair-mls-keys", "avs", "confirmations", "pending-deliveries",
                "sync", "local-events", "user-config-sync", "meeting-occurrences-sync", "calling",
            )
            assertEquals(expectedWorkers.associateWith { 1 }, workerStarts.groupingBy { it }.eachCount())
            assertEquals(listOf("migration", "persist-version", "reset-assets", "cleanup-stale-calls"), migrationEvents)
            verifySuspend(VerifyMode.exactly(1)) {
                metadata.insertValue("2", ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY)
                clients.registerToken("application", "client", "token", "APNS")
                pushTokens.setUpdateFirebaseTokenFlag(false)
                typing.clearExpiredTypingIndicators()
                sendDelivery(deliveryConversation, listOf("pending-message"))
                pending.acknowledge(listOf(1L))
            }
            assertFalse(pushUpdates.value)
            assertTrue(pendingRows.isEmpty())
            assertTrue(uncaughtFailures.isEmpty())
            assertTrue(loggedErrors.isEmpty())
        }
    }

    @Test
    fun factoriesKeepTheirSynchronousCoroutineAndClientRegistrationBoundaries() = runTest {
        withFixture {
            runtime.start()

            assertEquals(listOf("initial-sync", "proposals", "sync", "work-scheduler", "work-scheduler"), resolutions)
            assertEquals(
                listOf("initial-sync", "pending-proposals", "nomad-hooks", "sync", "user-config-sync", "meeting-occurrences-sync"),
                workerStarts,
            )

            runCurrent()

            val coroutineFactories = listOf(
                "migrations", "messages", "calls", "push", "mls-recovery", "join-pending-mls", "recovery",
                "self-deletion", "e2ei", "typing", "repair", "confirmations", "pending-delivery", "local-events",
            )
            for (factory in coroutineFactories) assertEquals(1, resolutions.count { it == factory }, factory)
            assertEquals(2, clientWaits)
            assertFalse("avs" in resolutions)
            assertFalse("calling" in resolutions)
            assertFalse("avs" in workerStarts)
            assertFalse("calling" in workerStarts)

            runtime.start()
            clientReady.complete(Unit)
            runCurrent()
            runtime.start()
            runCurrent()

            assertEquals(2, clientWaits)
            for (worker in listOf("avs", "calling")) {
                assertEquals(1, resolutions.count { it == worker })
                assertEquals(1, workerStarts.count { it == worker })
            }
        }
    }

    @Test
    fun assetRepositoryAndStaleCallCleanupAreResolvedOnlyAfterMigrationFinishes() = runTest {
        withFixture {
            val finishMigration = CompletableDeferred<Unit>()
            migrate = {
                finishMigration.await()
                Either.Right(Unit)
            }

            runtime.start()
            runCurrent()

            assertEquals(listOf("migration"), migrationEvents)
            assertFalse("messages" in resolutions)
            assertFalse("calls" in resolutions)
            assertTrue("conversation-recovery" in workerStarts)
            assertTrue("confirmations" in workerStarts)

            finishMigration.complete(Unit)
            runCurrent()

            assertEquals(listOf("migration", "persist-version", "reset-assets", "cleanup-stale-calls"), migrationEvents)
        }
    }

    @Test
    fun returnedMigrationFailureStillResetsAssetsAndSignalsStaleCallCleanup() = runTest {
        withFixture {
            migrate = { Either.Left(StorageFailure.DataNotFound) }

            runtime.start()
            runCurrent()

            assertEquals(listOf("migration", "reset-assets", "cleanup-stale-calls"), migrationEvents)
            verifySuspend(VerifyMode.not) { metadata.insertValue(any(), any()) }
            assertTrue(loggedErrors.isEmpty())
            assertTrue(uncaughtFailures.isEmpty())
        }
    }

    @Test
    fun thrownMigrationFailureSkipsAssetResetButSignalsCleanupAndLeavesOtherWorkersRunning() = runTest {
        withFixture {
            val failure = IllegalStateException("Migration failed")
            migrate = { throw failure }

            runtime.start()
            runCurrent()

            assertEquals(listOf("migration", "cleanup-stale-calls"), migrationEvents)
            assertFalse("messages" in resolutions)
            assertLoggedStartupFailure(failure)
            assertTrue("conversation-recovery" in workerStarts)
            assertTrue("local-events" in workerStarts)
            assertEquals(1, pushUpdates.subscriptionCount.value)
            assertEquals(1, typingStates.subscriptionCount.value)
            assertTrue(uncaughtFailures.isEmpty())
        }
    }

    @Test
    fun migrationFactoryFailureIsCaughtInsideTheWorkerAndStillSignalsCleanup() = runTest {
        withFixture {
            val failure = IllegalStateException("Cannot resolve migration dependencies")
            migrationFactory = { throw failure }

            runtime.start()
            assertTrue(loggedErrors.isEmpty())
            assertTrue(migrationEvents.isEmpty())
            runCurrent()

            assertEquals(listOf("cleanup-stale-calls"), migrationEvents)
            assertFalse("messages" in resolutions)
            assertLoggedStartupFailure(failure)
            assertTrue("self-deletion" in workerStarts)
            assertTrue(uncaughtFailures.isEmpty())
        }
    }

    @Test
    fun assetRepositoryResolutionAndResetFailuresBothSignalStaleCallCleanup() = runTest {
        for (failDuringResolution in listOf(true, false)) {
            withFixture {
                val failure = IllegalStateException("Asset reset failed")
                if (failDuringResolution) messageFactory = { throw failure } else resetAssets = { throw failure }

                runtime.start()
                runCurrent()

                val expectedEvents = mutableListOf("migration", "persist-version")
                if (!failDuringResolution) expectedEvents += "reset-assets"
                expectedEvents += "cleanup-stale-calls"
                assertEquals(expectedEvents, migrationEvents)
                assertLoggedStartupFailure(failure)
                assertTrue(uncaughtFailures.isEmpty())
            }
        }
    }

    @Test
    fun migrationCancellationIsRethrownAfterSignallingStaleCallCleanup() = runTest {
        withFixture {
            migrate = { throw CancellationException("Migration cancelled") }

            runtime.start()
            runCurrent()

            assertTrue(requireNotNull(migrationJob).isCancelled)
            assertEquals(listOf("migration", "cleanup-stale-calls"), migrationEvents)
            assertFalse("messages" in resolutions)
            assertTrue(loggedErrors.isEmpty())
            assertTrue(uncaughtFailures.isEmpty())
            assertTrue("confirmations" in workerStarts)
        }
    }

    @Test
    fun sessionCancellationDuringMigrationSignalsCleanupAndNeverResolvesClientBoundWork() = runTest {
        withFixture {
            val finishMigration = CompletableDeferred<Unit>()
            migrate = {
                finishMigration.await()
                Either.Right(Unit)
            }
            runtime.start()
            runCurrent()
            assertEquals(listOf("migration"), migrationEvents)
            assertEquals(2, clientWaits)

            sessionJob.cancel()
            runCurrent()
            finishMigration.complete(Unit)
            clientReady.complete(Unit)
            runtime.start()
            runCurrent()

            assertEquals(listOf("migration", "cleanup-stale-calls"), migrationEvents)
            assertFalse("messages" in resolutions)
            assertFalse("avs" in resolutions)
            assertFalse("calling" in resolutions)
            assertTrue(requireNotNull(migrationJob).isCancelled)
            assertTrue(loggedErrors.isEmpty())
            assertTrue(uncaughtFailures.isEmpty())
        }
    }

    @Test
    fun realPushAndTypingWorkersReactToChangesAndStopWithTheSession() = runTest {
        withFixture {
            clientIds.value = null
            runtime.start()
            runCurrent()

            verifySuspend(VerifyMode.not) { clients.registerToken(any(), any(), any(), any()) }
            verifySuspend(VerifyMode.exactly(1)) { typing.clearExpiredTypingIndicators() }
            assertEquals(1, typingRepositoryResolutions)

            clientIds.value = ClientId("client")
            typingStates.value = SyncState.SlowSync
            runCurrent()

            verifySuspend(VerifyMode.exactly(1)) { clients.registerToken("application", "client", "token", "APNS") }
            verifySuspend(VerifyMode.exactly(1)) { typing.clearExpiredTypingIndicators() }
            assertFalse(pushUpdates.value)
            assertEquals(1, typingRepositoryResolutions)
            assertEquals(1, clientIds.subscriptionCount.value)

            sessionJob.cancel()
            runCurrent()
            assertEquals(0, pushUpdates.subscriptionCount.value)
            assertEquals(0, clientIds.subscriptionCount.value)
            assertEquals(0, typingStates.subscriptionCount.value)

            clientIds.value = ClientId("another-client")
            pushUpdates.value = true
            typingStates.value = SyncState.Live
            runtime.start()
            runCurrent()

            verifySuspend(VerifyMode.not) { clients.registerToken(any(), any(), any(), any()) }
            verifySuspend(VerifyMode.not) { typing.clearExpiredTypingIndicators() }
        }
    }

    @Test
    fun realPendingDeliveryWorkerWaitsForLiveSyncThenSendsAndAcknowledgesDurableWork() = runTest {
        withFixture {
            runtime.start()
            runCurrent()

            assertEquals(1, workerStarts.count { it == "pending-deliveries" })
            verifySuspend(VerifyMode.not) {
                pending.getPendingAfter(any(), any())
                sendDelivery(any(), any())
                pending.acknowledge(any())
            }
            assertEquals(1, pendingRows.size)

            // This worker has a sync gate of its own; AVS/calling are still awaiting registration.
            syncLive.complete(Unit)
            runCurrent()
            runtime.start()
            runCurrent()

            verifySuspend(VerifyMode.exactly(1)) {
                pendingSync.waitUntilLive()
                sendDelivery(deliveryConversation, listOf("pending-message"))
                pending.acknowledge(listOf(1L))
            }
            assertTrue(pendingRows.isEmpty())
            assertFalse("avs" in resolutions)
            assertFalse("calling" in resolutions)
        }
    }

    @Test
    fun unrelatedWorkerFactoryFailureOccursInItsCoroutineWithoutPreventingOtherStartupWork() = runTest {
        withFixture {
            val failure = IllegalStateException("Cannot resolve conversation recovery")
            recoveryFactory = { throw failure }

            runtime.start()
            assertTrue(uncaughtFailures.isEmpty())
            runCurrent()

            assertEquals(listOf<Throwable>(failure), uncaughtFailures)
            assertTrue("self-deletion" in workerStarts)
            assertTrue("local-events" in workerStarts)
            assertTrue("stale-call-cleanup" in workerStarts)
            assertEquals(1, pushUpdates.subscriptionCount.value)
            assertEquals(1, typingStates.subscriptionCount.value)
        }
    }

    private suspend fun TestScope.withFixture(test: suspend Fixture.() -> Unit) {
        val fixture = Fixture(this)
        try {
            fixture.test()
        } finally {
            fixture.sessionJob.cancel()
            runCurrent()
        }
    }

    /** All four final workers are real; only their interface ports are mocked. */
    private class Fixture(testScope: TestScope) {
        val sessionJob = SupervisorJob()
        val uncaughtFailures = mutableListOf<Throwable>()
        private val scope = CoroutineScope(
            sessionJob + StandardTestDispatcher(testScope.testScheduler) +
                CoroutineExceptionHandler { _, failure -> uncaughtFailures += failure }
        )
        val workerStarts = mutableListOf<String>()
        val resolutions = mutableListOf<String>()
        val migrationEvents = mutableListOf<String>()
        val loggedErrors = mutableListOf<Pair<String, Throwable?>>()
        private val logger = KaliumLogger(
            config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.ERROR,
                initialLogWriterList = listOf(
                    object : LogWriter() {
                        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                            if (severity == Severity.Error) loggedErrors += message to throwable
                        }
                    }
                ),
            ),
            tag = "UserSessionRuntimeTest",
        )
        val clientReady = CompletableDeferred<Unit>()
        val syncLive = CompletableDeferred<Unit>()
        var clientWaits = 0
        var migrationJob: Job? = null
        var migrate: suspend () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        var resetAssets: suspend () -> Unit = {}
        val metadata = mock<MetadataDAO>()
        private val messages = mock<MessageRepository>()
        private val calls = mock<CallRepository>()
        private val proposals = mock<PendingProposalScheduler>()
        private val mlsRecovery = mock<MLSConversationsRecoveryManager>()
        private val joinPendingMLS = mock<JoinPendingMLSConversationsManager>()
        private val recovery = mock<ConversationsRecoveryManager>()
        private val selfDeletion = mock<EphemeralMessageDeletionHandler>()
        private val e2ei = mock<ObserveE2EIConversationsVerificationStatusesUseCase>()
        private val repair = mock<MLSFaultyKeysConversationsRepairUseCase>()
        private val avs = mock<AvsSyncStateReporter>()
        private val confirmations = mock<ConfirmationDeliveryHandler>()
        private val localEvents = mock<LocalEventManager>()
        private val workScheduler = mock<UserSessionWorkScheduler>()
        private val calling = mock<CallBackgroundManager>()
        val clients = mock<ClientRepository>()
        val pushTokens = mock<PushTokenRepository>()
        private val notificationTokens = mock<NotificationTokenRepository>()
        private val sessions = mock<SessionRepository>()
        val pushUpdates = MutableStateFlow(true)
        val clientIds = MutableStateFlow<ClientId?>(ClientId("client"))
        val typingStates = MutableStateFlow<SyncState>(SyncState.Waiting)
        val typing = mock<TypingIndicatorIncomingRepository>()
        private val observeSyncState = mock<ObserveSyncStateUseCase>()
        var typingRepositoryResolutions = 0
        val pending = mock<PendingDeliveryConfirmationDAO>()
        private val conversations = mock<ConversationRepository>()
        val sendDelivery = mock<SendDeliverSignalUseCase>()
        val pendingSync = mock<SyncStateObserver>()
        val deliveryConversation = TestConversation.CONVERSATION.copy(type = Conversation.Type.OneOnOne)
        val pendingRows = mutableListOf(
            PendingDeliveryConfirmationEntity(1L, deliveryConversation.id.toDao(), "pending-message")
        )
        private val migration = object : ApiMigration {
            override suspend fun invoke(): Either<CoreFailure, Unit> {
                workerStarts += "api-migration"
                migrationEvents += "migration"
                migrationJob = currentCoroutineContext()[Job]
                return migrate()
            }
        }
        var migrationFactory: () -> ApiMigrationManager = { ApiMigrationManager(2, metadata, listOf(2 to migration)) }
        var messageFactory: () -> MessageRepository = { messages }
        var recoveryFactory: () -> ConversationsRecoveryManager = { recovery }
        private val sync = object : SyncExecutor() {
            override fun startAndStopSyncAsNeeded() {
                workerStarts += "sync"
            }

            override suspend fun stopForRestart(): Unit = error("Startup must not stop sync")

            override suspend fun <T> request(executorAction: suspend SyncRequest.() -> T): T =
                error("Startup must not request sync through this port")
        }

        val runtime by lazy {
            UserSessionRuntime(
                scope = scope,
                logger = logger,
                startInitialSyncMonitoring = { resolve("initial-sync") { workerStarts += "initial-sync" } },
                registerNomadHooks = { workerStarts += "nomad-hooks" },
                waitUntilClientIdIsAvailable = {
                    clientWaits++
                    clientReady.await()
                },
                pendingProposalScheduler = { resolve("proposals") { proposals } },
                apiMigrationManager = { resolve("migrations", migrationFactory) },
                messageRepository = { resolve("messages", messageFactory) },
                callRepository = { resolve("calls") { calls } },
                pushTokenUpdater = {
                    resolve("push") {
                        PushTokenUpdater(clients, notificationTokens, pushTokens, sessions, UserId("user", "domain"))
                    }
                },
                mlsConversationsRecoveryManager = { resolve("mls-recovery") { mlsRecovery } },
                joinPendingMLSConversationsManager = { resolve("join-pending-mls") { joinPendingMLS } },
                conversationsRecoveryManager = { resolve("recovery", recoveryFactory) },
                ephemeralMessageDeletionHandler = { resolve("self-deletion") { selfDeletion } },
                observeE2EIConversationsVerificationStatuses = { resolve("e2ei") { e2ei } },
                typingIndicatorSyncManager = {
                    resolve("typing") {
                        TypingIndicatorSyncManager(
                            lazy {
                                typingRepositoryResolutions++
                                typing
                            },
                            observeSyncState,
                            logger,
                        )
                    }
                },
                mlsFaultyKeysConversationsRepair = { resolve("repair") { repair } },
                avsSyncStateReporter = { resolve("avs") { avs } },
                confirmationDeliveryHandler = { resolve("confirmations") { confirmations } },
                pendingDeliveryWorker = {
                    resolve("pending-delivery") { MainAppPendingDeliveryWorker(pending, conversations, sendDelivery, pendingSync) }
                },
                syncExecutor = { resolve("sync") { sync } },
                localEventManager = { resolve("local-events") { localEvents } },
                workScheduler = { resolve("work-scheduler") { workScheduler } },
                callBackgroundManager = { resolve("calling") { calling } },
            )
        }

        init {
            every { proposals.start() } calls { workerStarts += "pending-proposals" }
            everySuspend { metadata.valueByKey(ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY) } returns "1"
            everySuspend { metadata.insertValue("2", ApiMigrationManager.LAST_API_VERSION_IN_USE_KEY) } calls {
                migrationEvents += "persist-version"
            }
            everySuspend { messages.resetAssetTransferStatus() } calls {
                workerStarts += "asset-reset"
                migrationEvents += "reset-assets"
                resetAssets()
            }
            every { calls.setStaleOpenCallsCleanupFinished() } calls {
                workerStarts += "stale-call-cleanup"
                migrationEvents += "cleanup-stale-calls"
            }
            everySuspend { mlsRecovery.invoke() } calls { workerStarts += "mls-recovery" }
            everySuspend { joinPendingMLS.invoke() } calls { workerStarts += "join-pending-mls" }
            everySuspend { recovery.invoke() } calls { workerStarts += "conversation-recovery" }
            everySuspend { selfDeletion.enqueuePendingSelfDeletionMessages() } calls { workerStarts += "self-deletion" }
            everySuspend { e2ei.invoke() } calls { workerStarts += "e2ei" }
            everySuspend { repair.invoke() } calls { workerStarts += "repair-mls-keys" }
            everySuspend { avs.execute() } calls { workerStarts += "avs" }
            everySuspend { confirmations.sendPendingConfirmations() } calls { workerStarts += "confirmations" }
            every { localEvents.startProcessing() } calls { workerStarts += "local-events" }
            every { workScheduler.schedulePeriodicUserConfigSync() } calls { workerStarts += "user-config-sync" }
            every { workScheduler.schedulePeriodicMeetingOccurrencesSync() } calls { workerStarts += "meeting-occurrences-sync" }
            everySuspend { calling.startProcessing() } calls { workerStarts += "calling" }
            every { pushTokens.observeUpdateFirebaseTokenFlag() } calls {
                workerStarts += "push-tokens"
                pushUpdates
            }
            everySuspend { clients.observeCurrentClientId() } returns clientIds
            every { notificationTokens.getNotificationToken() } returns Either.Right(NotificationToken("token", "APNS", "application"))
            everySuspend { sessions.isNativePushSupportedByServer(any()) } returns Either.Right(true)
            everySuspend { clients.registerToken(any(), any(), any(), any()) } returns Either.Right(Unit)
            everySuspend { pushTokens.setUpdateFirebaseTokenFlag(any()) } calls { (value: Boolean) ->
                pushUpdates.value = value
                Either.Right(Unit)
            }
            every { observeSyncState.invoke() } calls {
                workerStarts += "typing"
                typingStates
            }
            everySuspend { typing.clearExpiredTypingIndicators() } returns Unit
            everySuspend { pendingSync.waitUntilLive() } calls {
                workerStarts += "pending-deliveries"
                syncLive.await()
            }
            everySuspend { pending.getPendingAfter(any(), any()) } calls { (after: Long, limit: Long) ->
                pendingRows.filter { it.id > after }.sortedBy { it.id }.take(limit.toInt())
            }
            everySuspend { conversations.getConversationById(deliveryConversation.id) } returns Either.Right(deliveryConversation)
            everySuspend { sendDelivery(any(), any()) } returns MessageOperationResult.Success
            everySuspend { pending.acknowledge(any()) } calls { (ids: List<Long>) ->
                pendingRows.removeAll { it.id in ids }
                Unit
            }
        }

        fun assertLoggedStartupFailure(failure: Throwable) {
            assertEquals(1, loggedErrors.size)
            assertEquals("Unable to run startup migrations and reset tasks", loggedErrors.single().first)
            assertSame(failure, loggedErrors.single().second)
        }

        private fun <T> resolve(name: String, factory: () -> T): T {
            resolutions += name
            return factory()
        }
    }
}
