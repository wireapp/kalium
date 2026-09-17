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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MLSClientManagerTest {
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun givenMLSClientIsNotRegisteredAndMLSSupportIsDisabled_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        testScope.runTest {
            val (arrangement, mlsClientManager) = Arrangement()
                .withSyncStates(Unit.right())
                .withIsAllowedToRegisterMLSClient(false)
                .withHasRegisteredMLSClient(Either.Right(false))
                .arrange(testScope)

            mlsClientManager.invoke()
            advanceUntilIdle()


            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientIsNotRegisteredAndMLSSupportIsEnabled_whenObservingSyncFinishes_thenMLSClientIsRegistered() =
        testScope.runTest {
            val (arrangement, mlsClientManager) = Arrangement()
                .withIsAllowedToRegisterMLSClient(true)
                .withHasRegisteredMLSClient(Either.Right(false))
                .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
                .withRegisterMLSClientSuccessful()
                .withSyncStates(Unit.right())
                .arrange(testScope)

            mlsClientManager.invoke()
            advanceUntilIdle()


            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }.wasInvoked(once)
        }

    @Test
    fun givenE2EIIsRequired_whenObservingSyncFinishes_thenSlowSyncIsNotCleared() =
        testScope.runTest {
            val (arrangement, mlsClientManager) = Arrangement()
                .withIsAllowedToRegisterMLSClient(true)
                .withHasRegisteredMLSClient(Either.Right(false))
                .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
                .withRegisterMLSClientE2EIRequired()
                .withSyncStates(Unit.right())
                .arrange(testScope)

            mlsClientManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientIsRegistered_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        testScope.runTest {
            val (arrangement, mlsClientManager) = Arrangement()
                .withIsAllowedToRegisterMLSClient(true)
                .withSyncStates(Unit.right())
                .withHasRegisteredMLSClient(Either.Right(true))
                .arrange(testScope)

            mlsClientManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientIsRegistered_whenObservingSyncFinishes_thenDoNotEvenCheckIfIsAllowedToRegisterMLSClient() =
        testScope.runTest {
            val (arrangement, mlsClientManager) = Arrangement()
                .withHasRegisteredMLSClient(Either.Right(true))
                .withSyncStates(Unit.right())
                .arrange(testScope)

            mlsClientManager.invoke()
            advanceUntilIdle()

            coVerify {
                arrangement.isAllowedToRegisterMLSClient()
            }.wasNotInvoked()
        }

    @Test
    fun givenSyncFails_whenInvoked_thenRegistrationIsNotChecked() = testScope.runTest {
        val (arrangement, manager) = Arrangement()
            .withSyncStates(Either.Left(CoreFailure.Unknown(null)))
            .arrange(testScope)
        manager()
        coVerify { arrangement.clientRepository.hasRegisteredMLSClient() }.wasNotInvoked()
        coVerify { arrangement.registerMLSClient(any()) }.wasNotInvoked()
    }

    @Test
    fun givenMLSBecomesEnabledBeforeLive_whenForegroundCheckRuns_thenClientIsRegistered() = testScope.runTest {
        val arrangement = Arrangement()
            .withHasRegisteredMLSClient(Either.Right(false))
            .withIsAllowedToRegisterMLSClient(false)
            .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
            .withRegisterMLSClientSuccessful()
        val live = CompletableDeferred<Unit>()
        coEvery { arrangement.syncStateObserver.waitUntilLiveOrFailure() }.invokes {
            live.await()
            Unit.right()
        }
        val (_, manager) = arrangement.arrange(testScope)
        launch { manager() }
        runCurrent()
        coVerify { arrangement.isAllowedToRegisterMLSClient() }.wasNotInvoked()
        arrangement.withIsAllowedToRegisterMLSClient(true)
        live.complete(Unit)
        advanceUntilIdle()
        coVerify { arrangement.registerMLSClient(any()) }.wasInvoked(once)
    }

    @Test
    fun givenSyncIsPending_whenInvoked_thenEligibilityWaitsForSync() = testScope.runTest {
        val arrangement = Arrangement().withHasRegisteredMLSClient(Either.Right(true))
        val live = CompletableDeferred<Unit>()
        coEvery { arrangement.syncStateObserver.waitUntilLiveOrFailure() }.invokes {
            live.await()
            Unit.right()
        }
        val (_, manager) = arrangement.arrange(testScope)
        launch { manager() }
        runCurrent()
        coVerify { arrangement.clientRepository.hasRegisteredMLSClient() }.wasNotInvoked()
        live.complete(Unit)
        advanceUntilIdle()
        coVerify { arrangement.clientRepository.hasRegisteredMLSClient() }.wasInvoked(once)
    }

    @Test
    fun givenFirstAttemptSucceeds_whenCallersCancel_thenQueuedCheckReadsRegisteredState() = testScope.runTest {
        verifyQueuedRegistration(firstSucceeds = true)
    }

    @Test
    fun givenFirstAttemptFails_whenCallersCancel_thenQueuedCheckRetries() = testScope.runTest {
        verifyQueuedRegistration(firstSucceeds = false)
    }

    private suspend fun verifyQueuedRegistration(firstSucceeds: Boolean) {
        val arrangement = Arrangement()
            .withSyncStates(Unit.right())
            .withIsAllowedToRegisterMLSClient(true)
            .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
        val completion = CompletableDeferred<Unit>()
        var registered = false
        var attempts = 0
        var reads = 0
        coEvery { arrangement.clientRepository.hasRegisteredMLSClient() }.invokes {
            reads++
            Either.Right(registered)
        }
        coEvery { arrangement.registerMLSClient(any()) }.invokes {
            attempts++
            if (attempts == 1) {
                completion.await()
                if (firstSucceeds) {
                    registered = true
                    Either.Right(RegisterMLSClientResult.Success)
                } else {
                    Either.Left(CoreFailure.Unknown(null))
                }
            } else {
                registered = true
                Either.Right(RegisterMLSClientResult.Success)
            }
        }
        val (_, manager) = arrangement.arrange(testScope)
        val first = testScope.launch { manager() }
        testScope.runCurrent()
        val second = testScope.launch { manager() }
        testScope.runCurrent()
        assertEquals(1, attempts)
        assertEquals(1, reads)
        first.cancel()
        second.cancel()
        testScope.runCurrent()
        completion.complete(Unit)
        testScope.advanceUntilIdle()
        assertEquals(2, reads)
        assertEquals(if (firstSucceeds) 1 else 2, attempts)
        coVerify { arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant() }.wasInvoked(once)
    }

    @Test
    fun givenEligibilityChanges_whenSecondCheckQueues_thenItRegisters() = testScope.runTest {
        val arrangement = Arrangement()
            .withSyncStates(Unit.right())
            .withHasRegisteredMLSClient(Either.Right(false))
            .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
            .withRegisterMLSClientSuccessful()
        val eligibility = CompletableDeferred<Unit>()
        var checks = 0
        coEvery { arrangement.isAllowedToRegisterMLSClient() }.invokes {
            checks++
            if (checks == 1) {
                eligibility.await()
                false
            } else {
                true
            }
        }
        val (_, manager) = arrangement.arrange(testScope)
        launch { manager() }
        runCurrent()
        launch { manager() }
        runCurrent()
        assertEquals(1, checks)
        eligibility.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, checks)
        coVerify { arrangement.registerMLSClient(any()) }.wasInvoked(once)
    }

    @Test
    fun givenDistinctIODispatcher_whenRegistering_thenUserScopedWorkUsesIO() = testScope.runTest {
        val io = StandardTestDispatcher(testScheduler, name = "registration-io")
        val arrangement = Arrangement()
            .withSyncStates(Unit.right())
            .withHasRegisteredMLSClient(Either.Right(false))
            .withIsAllowedToRegisterMLSClient(true)
            .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
        coEvery { arrangement.registerMLSClient(any()) }.invokes {
            assertEquals(io, currentCoroutineContext()[ContinuationInterceptor])
            Either.Right(RegisterMLSClientResult.Success)
        }
        val (_, manager) = arrangement.arrange(testScope, io.testKaliumDispatcher())
        manager()
        coVerify { arrangement.registerMLSClient(any()) }.wasInvoked(once)
    }

    @Test
    fun givenRegistrationFails_whenRetried_thenLockIsReleased() = testScope.runTest {
        val arrangement = Arrangement()
            .withSyncStates(Unit.right())
            .withHasRegisteredMLSClient(Either.Right(false))
            .withIsAllowedToRegisterMLSClient(true)
            .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
        coEvery { arrangement.registerMLSClient(any()) }.returns(Either.Left(CoreFailure.Unknown(null)))
        val (_, manager) = arrangement.arrange(testScope)
        manager()
        arrangement.withRegisterMLSClientSuccessful()
        manager()
        coVerify { arrangement.registerMLSClient(any()) }.wasInvoked(exactly = 2)
        coVerify { arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant() }.wasInvoked(once)
    }

    private class Arrangement {

        val syncStateObserver: SyncStateObserver = mock(SyncStateObserver::class)
        var slowSyncRepository = mock(SlowSyncRepository::class)
        var clientIdProvider = mock(CurrentClientIdProvider::class)
        val clientRepository = mock(ClientRepository::class)
        val isAllowedToRegisterMLSClient = mock(IsAllowedToRegisterMLSClientUseCase::class)
        val registerMLSClient = mock(RegisterMLSClientUseCase::class)

        suspend fun withCurrentClientId(result: Either<CoreFailure, ClientId>) = apply {
            coEvery {
                clientIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withHasRegisteredMLSClient(result: Either<CoreFailure, Boolean>) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(result)
        }

        suspend fun withRegisterMLSClientSuccessful() = apply {
            coEvery {
                registerMLSClient.invoke(any())
            }.returns(Either.Right(RegisterMLSClientResult.Success))
        }

        suspend fun withRegisterMLSClientE2EIRequired() = apply {
            coEvery {
                registerMLSClient.invoke(any())
            }.returns(Either.Right(RegisterMLSClientResult.E2EICertificateRequired))
        }

        suspend fun withIsAllowedToRegisterMLSClient(enabled: Boolean) = apply {
            coEvery {
                isAllowedToRegisterMLSClient()
            }.returns(enabled)
        }
        suspend fun withSyncStates(result : Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncStateObserver.waitUntilLiveOrFailure()
            }.returns(result)
        }

        fun arrange(
            testScope: TestScope,
            dispatchers: KaliumDispatcher = StandardTestDispatcher(testScope.testScheduler).testKaliumDispatcher()
        ) = this to MLSClientManagerImpl(
            clientIdProvider,
            isAllowedToRegisterMLSClient,
            syncStateObserver,
            lazy { slowSyncRepository },
            lazy { clientRepository },
            lazy { registerMLSClient },
            testScope,
            dispatchers
        )
    }
}
