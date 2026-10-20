/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.feature.backup.BackupScope
import com.wire.kalium.logic.feature.backup.LegacyBackupDependencies
import com.wire.kalium.logic.feature.backup.LegacyBackupGraph
import com.wire.kalium.logic.feature.backup.LegacyMultiPlatformBackupDependencies
import com.wire.kalium.logic.feature.backup.LegacyMultiPlatformBackupGraph
import com.wire.kalium.logic.feature.backup.MultiPlatformBackupScope
import com.wire.kalium.logic.sync.SyncStateObserver
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Exercises generated production extensions, including their per-facade scope caches. */
class OperationFacadeBindingIdentityTest {
    @Test
    fun freshCallUseCasesKeepTheirFacadeCaptureAndDoNotOpenTheCallingRuntime() {
        val parent = createGraph<OperationFacadeTestGraph>()
        val calls = mock<CallRepository>()
        val established = MutableStateFlow<List<Call>>(emptyList())
        every { calls.establishedCallsFlow() } returns established
        val manager = lazy<CallManager> { error("Observation must not open AVS") }
        val sync = lazy<SyncStateObserver> { error("Observation must not start sync") }
        val first = parent.callsFactory.create(callInputs(calls, manager, sync))
        val facade = CallsScope(first)

        assertNotSame(facade.establishedCall, facade.establishedCall)
        assertSame(established, facade.establishedCall())
        assertSame(established, facade.establishedCall())
        assertSame(manager, first.callsCallManager.value)
        assertSame(sync, first.callsSyncStateObserver.value)
        assertFalse(manager.isInitialized())
        assertFalse(sync.isInitialized())
        assertSame(first.callsObserveOngoingAndIncomingCalls, facade.observeOngoingAndIncomingCalls)
        assertSame(facade.updateNextTimeCallFeedback, facade.updateNextTimeCallFeedback)
        assertSame(first.callsShouldAskCallFeedback, first.callsShouldAskCallFeedback)
        assertNotSame(facade.observeAskCallFeedbackUseCase, facade.observeAskCallFeedbackUseCase)

        val second = parent.callsFactory.create(callInputs(calls, manager, sync))
        assertSame(calls, second.callsCallRepository)
        assertNotSame(first.callsObserveOngoingAndIncomingCalls, second.callsObserveOngoingAndIncomingCalls)
        assertNotSame(first.callsShouldAskCallFeedback, second.callsShouldAskCallFeedback)
        assertNotSame(first.callsUpdateNextTimeCallFeedback, second.callsUpdateNextTimeCallFeedback)
        assertNotSame(first.callsCallingParticipantsOrder, first.callsCallingParticipantsOrder)
    }

    @Test
    fun concurrentFirstResolutionRetainsOneFeedbackUseCaseInTheFacadeGraph() = runTest {
        val graph = createGraph<OperationFacadeTestGraph>().callsFactory.create(callInputs())
        val requests = List(12) { async(Dispatchers.Default) { graph.callsShouldAskCallFeedback } }.awaitAll()
        requests.forEach { assertSame(requests.first(), it) }
    }

    @Test
    fun differentFacadeCapturesDoNotBecomeAUserSingleton() {
        val parent = createGraph<OperationFacadeTestGraph>()
        val firstInputs = callInputs()
        val secondInputs = callInputs()
        val first = parent.callsFactory.create(firstInputs)
        val second = parent.callsFactory.create(secondInputs)
        assertSame(firstInputs.conversationRepository, first.callsConversationRepository)
        assertSame(secondInputs.conversationRepository, second.callsConversationRepository)
        assertNotSame(first.callsConversationRepository, second.callsConversationRepository)
        assertSame(firstInputs.callRepository, first.callsCallRepository)
        assertNotSame(first.callsCallRepository, second.callsCallRepository)
    }

    @Test
    fun backupRetainsItsOwnCryptoOperationHelperAndMutexButKeepsVerifyFresh() = runTest {
        val parent = createGraph<OperationFacadeTestGraph>()
        val inputs = backupInputs(backgroundScope)
        val first = parent.backupFactory.create(inputs)
        val second = parent.backupFactory.create(inputs)
        val facade = BackupScope(first)
        assertNotSame(facade.verify, facade.verify)
        assertSame(facade.backupCryptoDB, facade.backupCryptoDB)
        assertSame(facade.backupAndUploadCryptoState, facade.backupAndUploadCryptoState)
        assertNotSame(first.backupBackupCryptoDB, second.backupBackupCryptoDB)
        assertNotSame(first.backupBackupAndUploadCryptoState, second.backupBackupAndUploadCryptoState)
        assertNotSame(first.backupSecurityHelper, second.backupSecurityHelper)
        assertNotSame(first.backupBackupAndUploadCryptoStateMutex, second.backupBackupAndUploadCryptoStateMutex)
        assertSame(inputs.originatingScope, first.backupOriginatingScope)
        assertSame(inputs.cryptoTransactionProvider, second.backupCryptoTransactionProvider)

        // Holding the generated graph's actual mutex must block both reads of the cached upload operation.
        // Cancel before unlocking: crypto/native/file operations must never be reached in this test.
        val mutex = first.backupBackupAndUploadCryptoStateMutex
        mutex.lock()
        val one = async { facade.backupAndUploadCryptoState() }
        val two = async { facade.backupAndUploadCryptoState() }
        try {
            runCurrent()
            assertFalse(one.isCompleted)
            assertFalse(two.isCompleted)
            assertFalse(second.backupBackupAndUploadCryptoStateMutex.isLocked)
        } finally {
            one.cancelAndJoin()
            two.cancelAndJoin()
            mutex.unlock()
        }
    }

    @Test
    fun multiplatformRestoreVariantsStayFreshAndKeepTheCapturedRepositoryAndCallback() = runTest {
        val parent = createGraph<OperationFacadeTestGraph>()
        var waits = 0
        val waitUntilLive: suspend () -> Unit = { waits++ }
        val inputs = object : LegacyMultiPlatformBackupDependencies {
            override val selfUserId = QualifiedID("self", "example.com")
            override val kaliumFileSystem = mock<KaliumFileSystem>()
            override val backupRepository = mock<BackupRepository>()
            override val userRepository = mock<UserRepository>()
            override val refreshConversationsWithoutMetadata = mock<RefreshConversationsWithoutMetadataUseCase>()
            override val userSessionScope: CoroutineScope = backgroundScope
            override val waitUntilSyncLive = waitUntilLive
        }
        val graph = parent.mpFactory.create(inputs)
        val facade = MultiPlatformBackupScope(graph)
        assertNotSame(facade.create, facade.create)
        assertNotSame(facade.restore, facade.restore)
        assertNotSame(facade.restore(Dispatchers.Default), facade.restore(Dispatchers.Default))
        assertSame(inputs.backupRepository, graph.mpBackupBackupRepository)
        assertSame(backgroundScope, graph.mpBackupUserSessionScope)
        assertSame(waitUntilLive, graph.mpBackupWaitUntilSyncLive.value)
        assertEquals(0, waits)
        graph.mpBackupWaitUntilSyncLive.value()
        assertEquals(1, waits)
        val secondInputs = object : LegacyMultiPlatformBackupDependencies by inputs {
            override val backupRepository = mock<BackupRepository>()
        }
        assertNotSame(graph.mpBackupBackupRepository, parent.mpFactory.create(secondInputs).mpBackupBackupRepository)
    }

    private fun callInputs(
        repository: CallRepository = mock(),
        manager: Lazy<CallManager> = lazy { error("Unused calling runtime") },
        sync: Lazy<SyncStateObserver> = lazy { error("Unused sync runtime") },
    ): LegacyCallsDependencies = object : LegacyCallsDependencies by mock<LegacyCallsDependencies>() {
        override val callManager = manager
        override val syncStateObserver = sync
        override val callRepository = repository
        override val conversationRepository = mock<ConversationRepository>()
        override val currentClientIdProvider = mock<CurrentClientIdProvider>()
        override val qualifiedIdMapper = mock<QualifiedIdMapper>()
        override val userConfigRepository = mock<UserConfigRepository>()
        override val selfUserId = QualifiedID("self", "example.com")
    }

    private fun backupInputs(scope: CoroutineScope): LegacyBackupDependencies =
        object : LegacyBackupDependencies by mock<LegacyBackupDependencies>() {
            override val userId = QualifiedID("self", "example.com")
            override val clientIdProvider = mock<CurrentClientIdProvider>()
            override val kaliumFileSystem = mock<KaliumFileSystem>()
            override val cryptoTransactionProvider = mock<CryptoTransactionProvider>()
            override val eventRepository = mock<EventRepository>()
            override val cryptoStateBackupRemoteRepository = mock<CryptoStateBackupRemoteRepository>()
            override val originatingScope = scope
            override val passphraseStorage = mock<PassphraseStorage>()
        }

}

@DependencyGraph
internal interface OperationFacadeTestGraph {
    val callsFactory: LegacyCallsGraph.Factory
    val backupFactory: LegacyBackupGraph.Factory
    val mpFactory: LegacyMultiPlatformBackupGraph.Factory
}
