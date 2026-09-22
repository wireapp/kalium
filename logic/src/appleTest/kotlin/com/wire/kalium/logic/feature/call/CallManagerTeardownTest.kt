/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.AppleAvsBridge
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTeardownTest {
    @Test
    fun closingBeforeCreationDoesNotStartOrReleaseNativeHandle() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val manager = fixture.manager()
        manager.cancelJobs()
        manager.cancelJobs()
        verifySuspend(VerifyMode.exactly(0)) { fixture.avs.releaseUserHandle(any()) }
    }

    @Test
    fun closeWaitsForNativeShutdownAndConcurrentCloseReleasesOnce() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val shutdown = CompletableDeferred<Unit>()
        everySuspend { fixture.avs.releaseUserHandle(HANDLE) } calls { shutdown.await() }
        val manager = fixture.manager()
        manager.waitUntilInitialized()
        val first = launch { manager.cancelJobs() }
        val second = launch { manager.cancelJobs() }
        runCurrent()
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        shutdown.complete(Unit)
        first.join()
        second.join()
        manager.cancelJobs()
        verifySuspend(VerifyMode.exactly(1)) { fixture.avs.releaseUserHandle(HANDLE) }
    }

    @Test
    fun cancellingCreationStillReleasesTheReturnedNativeHandle() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val manager = fixture.manager()
        var close: Job? = null
        every { fixture.avs.userHandle(any(), any(), any()) } calls {
            // Cancels the creator while the synchronous C call is returning its handle.
            close = launch(start = CoroutineStart.UNDISPATCHED) { manager.cancelJobs() }
            HANDLE
        }
        assertFailsWith<CancellationException> { manager.waitUntilInitialized() }
        checkNotNull(close).join()
        verifySuspend(VerifyMode.exactly(1)) { fixture.avs.releaseUserHandle(HANDLE) }
    }

    @Test
    fun knownCreationFailureDoesNotRetainANativeHandle() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        every { fixture.avs.userHandle(any(), any(), any()) } returns null
        val manager = fixture.manager()
        assertFailsWith<IllegalStateException> { manager.waitUntilInitialized() }
        manager.cancelJobs()
        verifySuspend(VerifyMode.exactly(0)) { fixture.avs.releaseUserHandle(any()) }
    }

    @Test
    fun exceptionalCreationRetainsAccountOwnership() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        every { fixture.avs.userHandle(any(), any(), any()) } calls { error("unknown native creation state") }
        val manager = fixture.manager()
        assertFailsWith<IllegalStateException> { manager.waitUntilInitialized() }
        assertFailsWith<IllegalStateException> { manager.cancelJobs() }
        assertFailsWith<IllegalStateException> { manager.cancelJobs() }
        verifySuspend(VerifyMode.exactly(0)) { fixture.avs.releaseUserHandle(any()) }
    }

    @Test
    fun cancelledCloseRetainsHandleUntilACompletedRetry() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val shutdown = CompletableDeferred<Unit>()
        everySuspend { fixture.avs.releaseUserHandle(HANDLE) } calls { shutdown.await() }
        val manager = fixture.manager()
        manager.waitUntilInitialized()
        val first = launch { manager.cancelJobs() }
        runCurrent()
        first.cancelAndJoin()
        val retry = launch { manager.cancelJobs() }
        runCurrent()
        assertFalse(retry.isCompleted)
        shutdown.complete(Unit)
        retry.join()
        assertTrue(retry.isCompleted)
        manager.cancelJobs()
        verifySuspend(VerifyMode.exactly(2)) { fixture.avs.releaseUserHandle(HANDLE) }
    }

    @Test
    fun failedShutdownRetainsHandleForRetry() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        everySuspend { fixture.avs.releaseUserHandle(HANDLE) } calls { error("shutdown failed") }
        val manager = fixture.manager()
        manager.waitUntilInitialized()
        assertFailsWith<IllegalStateException> { manager.cancelJobs() }
        everySuspend { fixture.avs.releaseUserHandle(HANDLE) } returns Unit
        manager.cancelJobs()
        manager.cancelJobs()
        verifySuspend(VerifyMode.exactly(2)) { fixture.avs.releaseUserHandle(HANDLE) }
    }

    private class Fixture(private val dispatcher: KaliumDispatcher) {
        val avs = mock<AppleAvsBridge>(MockMode.autoUnit)
        private val clientId = mock<CurrentClientIdProvider>()
        private val federatedIdMapper = mock<FederatedIdMapper>()
        init {
            everySuspend { clientId() } returns ClientId("client").right()
            everySuspend { federatedIdMapper.parseToFederatedId(any<UserId>()) } returns "self@example.com"
            every { avs.userHandle(any(), any(), any()) } returns HANDLE
        }
        fun manager() = CallManagerImpl(
            callRepository = mock(),
            currentClientIdProvider = clientId,
            selfConversationIdProvider = mock(),
            messageSender = mock(),
            callMapper = mock(),
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = mock(),
            videoStateChecker = mock(),
            conversationClientsInCallUpdater = mock(),
            epochInfoUpdater = mock(),
            networkStateObserver = mock(),
            getCallConversationType = mock(),
            userConfigRepository = mock(),
            kaliumConfigs = KaliumConfigs(),
            mediaManagerService = mock(MockMode.autoUnit),
            flowManagerService = mock(MockMode.autoUnit),
            createAndPersistRecentlyEndedCallMetadata = mock(),
            selfUserId = UserId("self", "example.com"),
            kaliumDispatchers = dispatcher,
            avs = avs,
        )
    }

    private companion object {
        const val HANDLE = 42u
    }
}
