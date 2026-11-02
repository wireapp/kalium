/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.wire.kalium.logic.feature.share

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerSendingTest {
    @Test
    fun givenSlowSyncComplete_whenAwaitingImport_thenWaitForProcessedIncrementalLive() = runTest {
        val arrangement = Arrangement(backgroundScope)
        val waiting = async { arrangement.sending.awaitSynced() }
        runCurrent()
        assertFalse(waiting.isCompleted, "Slow sync complete with incremental sync pending is not enough")

        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
        runCurrent()
        assertFalse(waiting.isCompleted, "Pending events still have to be processed")

        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        waiting.await()
    }

    @Test
    fun givenAccountWasLive_whenTheNextCatchUpFails_thenImportStopsWaiting() = runTest {
        val arrangement = Arrangement(backgroundScope)
        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        arrangement.sending.awaitSynced()
        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)

        val waiting = async { arrangement.sending.awaitSynced() }
        runCurrent()
        assertFalse(waiting.isCompleted, "A previous live does not cover the next catch-up")

        // Offline, the import stores with the rules it has, as the composer does.
        arrangement.incremental.updateIncrementalSyncState(
            IncrementalSyncStatus.Failed(NetworkFailure.NoNetworkConnection(null), Duration.ZERO)
        )
        waiting.await()
    }

    @Test
    fun givenImportWaitingForLive_whenAppStopsServing_thenWaitingIsCancellable() = runTest {
        val arrangement = Arrangement(backgroundScope)
        var passedBarrier = false
        val waiting = async {
            arrangement.sending.awaitSynced()
            passedBarrier = true
        }
        runCurrent()
        waiting.cancelAndJoin()
        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        runCurrent()

        assertTrue(waiting.isCancelled)
        assertFalse(passedBarrier)
    }

    private class Arrangement(scope: CoroutineScope) {
        private val slowSync = mock<SlowSyncRepository> {
            every { slowSyncStatus } returns MutableStateFlow(SlowSyncStatus.Complete)
        }
        val incremental = InMemoryIncrementalSyncRepository()
        val sending = ComposerSending(
            messages = lazy { error("Waiting for sync must not initialize the composer") },
            syncStateObserver = SyncStateObserverImpl(slowSync, incremental, scope),
            cells = lazy { error("Waiting for sync must not initialize Cells") },
            conversations = mock<com.wire.kalium.logic.data.conversation.ConversationRepository>(),
        )
    }
}
