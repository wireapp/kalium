/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.wire.kalium.logic.feature.share

import com.wire.kalium.common.error.CoreFailure
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
    fun givenAccountWasLive_whenSyncRestarts_thenImportWaitsForTheNewCatchUp() = runTest {
        val arrangement = Arrangement(backgroundScope)
        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        arrangement.sending.awaitSynced()
        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Pending)

        val waiting = async { arrangement.sending.awaitSynced() }
        runCurrent()
        assertFalse(waiting.isCompleted)

        arrangement.incremental.updateIncrementalSyncState(
            IncrementalSyncStatus.Failed(CoreFailure.MissingClientRegistration, Duration.ZERO)
        )
        runCurrent()
        assertFalse(waiting.isCompleted, "A sync failure must not authorize importing against stale policy")

        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.FetchingPendingEvents)
        runCurrent()
        assertFalse(waiting.isCompleted)

        arrangement.incremental.updateIncrementalSyncState(IncrementalSyncStatus.Live)
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
        )
    }
}
