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

import com.wire.kalium.calling.LifecycleCalling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class CallManagerLifecycleTest {
    @Test
    fun mediaStartupFailurePropagatesBeforeHandleCreation() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
        val startupFailure = IllegalStateException("Fake media startup failure")
        everySuspend { arrangement.mediaManagerService.startMediaManager() } calls { throw startupFailure }
        val (_, manager) = arrangement.arrange()
        assertEquals(startupFailure.message, assertFailsWith<IllegalStateException> { manager.waitUntilInitialized() }.message)
        verify(VerifyMode.exactly(0)) {
            native.wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        manager.cancelJobs()
    }

    @Test
    fun cancelDuringReadinessReleasesPublishedHandleBeforeManagerScopeIsDiscarded() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
        lateinit var ready: ReadyHandler
        every {
            native.wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } calls { args -> ready = args.args[2] as ReadyHandler; HANDLE }
        val (_, manager) = arrangement.arrange()
        val initialization = async { runCatching { manager.waitUntilInitialized() } }
        runCurrent()
        manager.cancelJobs()
        ready.onReady(1, null)
        assertEquals(true, initialization.await().exceptionOrNull() is CancellationException)
        verifySuspend(VerifyMode.exactly(1)) { native.releaseHandle(HANDLE) }
    }

    @Test
    fun failedShutdownCanBeRetriedOnTheSameManager() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
            .onWcallCreateReturning(HANDLE)
        var releaseAttempts = 0
        everySuspend { native.releaseHandle(HANDLE) } calls {
            releaseAttempts++
            if (releaseAttempts == 1) error("Fake native shutdown timeout")
        }
        val (_, manager) = arrangement.arrange()
        manager.waitUntilInitialized()
        runCurrent()
        assertFailsWith<IllegalStateException> { manager.cancelJobs() }
        manager.cancelJobs()
        assertEquals(2, releaseAttempts)
    }

    @Test
    fun shutdownTimeoutDrainsPendingAccountCallbacksAndRejectsLateWorkBeforeRetry() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
        lateinit var config: CallConfigRequestHandler
        var configRequests = 0
        var configCancelled = false
        every {
            native.wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } calls { args ->
            config = args.args[11] as CallConfigRequestHandler
            (args.args[2] as ReadyHandler).onReady(1, null)
            HANDLE
        }
        everySuspend { arrangement.callRepository.getCallConfigResponse(any()) } calls {
            configRequests++
            try { awaitCancellation() } finally { configCancelled = true }
        }
        var releaseAttempts = 0
        everySuspend { native.releaseHandle(HANDLE) } calls {
            assertTrue(configCancelled)
            releaseAttempts++
            if (releaseAttempts == 1) withTimeout(1) { awaitCancellation() }
        }
        val (_, manager) = arrangement.arrange()
        manager.waitUntilInitialized()
        config.onConfigRequest(HANDLE, null)
        runCurrent()
        assertEquals(1, configRequests)
        assertFailsWith<TimeoutCancellationException> { manager.cancelJobs() }
        assertTrue(configCancelled)
        config.onConfigRequest(HANDLE, null)
        runCurrent()
        assertEquals(1, configRequests)
        manager.cancelJobs()
        assertEquals(2, releaseAttempts)
    }

    @Test
    fun nonFederatedNativeAliasStillBindsTheQualifiedAccountOwner() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
            .onWcallCreateReturning(HANDLE)
        arrangement.onParseToFederatedIdReturning(arrangement.selfUserId, arrangement.selfUserId.value)
        val (_, manager) = arrangement.arrange()
        manager.waitUntilInitialized()
        verify(VerifyMode.exactly(1)) { native.bindAccount(HANDLE, arrangement.selfUserId.toString()) }
        manager.cancelJobs()
    }

    @Test
    fun failedParticipationReadinessPreventsAnswerAndPreservesQualifiedConversation() = runTest {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val conversation = ConversationId("conversation", "domain")
        val arrangement = CallManagerTest().Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher(), native)
            .onWcallCreateReturning(HANDLE)
        arrangement.onParseToFederatedIdReturning(conversation, conversation.value)
        everySuspend { native.prepareCallMedia(HANDLE, conversation.value, conversation.toString(), true) } calls {
            error("Native devices did not acknowledge readiness")
        }
        val (_, manager) = arrangement.arrange()
        manager.waitUntilInitialized()
        assertFailsWith<IllegalStateException> { manager.answerCall(conversation, isAudioCbr = false, isVideoCall = true) }
        verifySuspend(VerifyMode.exactly(1)) {
            native.prepareCallMedia(HANDLE, conversation.value, conversation.toString(), true)
        }
        verify(VerifyMode.exactly(0)) { native.wcall_answer(any(), any(), any(), any()) }
        manager.cancelJobs()
    }

    private companion object {
        val HANDLE = Handle(42)
    }
}
