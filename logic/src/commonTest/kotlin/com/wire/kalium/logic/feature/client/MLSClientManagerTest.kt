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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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


            verifySuspend(VerifyMode.not) {
                arrangement.registerMLSClient.invoke(any())
            }
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


            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.registerMLSClient.invoke(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.registerMLSClient.invoke(any())
            }
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

            verifySuspend(VerifyMode.not) {
                arrangement.isAllowedToRegisterMLSClient()
            }
        }

    private class Arrangement {

        val syncStateObserver: SyncStateObserver = mock<SyncStateObserver>(mode = MockMode.autoUnit)
        var slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        var clientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val isAllowedToRegisterMLSClient = mock<IsAllowedToRegisterMLSClientUseCase>(mode = MockMode.autoUnit)
        val registerMLSClient = mock<RegisterMLSClientUseCase>(mode = MockMode.autoUnit)

        suspend fun withCurrentClientId(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend {
                clientIdProvider.invoke()
            } returns result
        }

        suspend fun withHasRegisteredMLSClient(result: Either<CoreFailure, Boolean>) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns result
        }

        suspend fun withRegisterMLSClientSuccessful() = apply {
            everySuspend {
                registerMLSClient.invoke(any())
            } returns Either.Right(RegisterMLSClientResult.Success)
        }

        suspend fun withRegisterMLSClientE2EIRequired() = apply {
            everySuspend {
                registerMLSClient.invoke(any())
            } returns Either.Right(RegisterMLSClientResult.E2EICertificateRequired)
        }

        suspend fun withIsAllowedToRegisterMLSClient(enabled: Boolean) = apply {
            everySuspend {
                isAllowedToRegisterMLSClient()
            } returns enabled
        }
        suspend fun withSyncStates(result : Either<CoreFailure, Unit>) = apply {
            everySuspend {
                syncStateObserver.waitUntilLiveOrFailure()
            } returns result
        }

        fun arrange(testScope: TestScope) = apply {
            everySuspend { slowSyncRepository.clearLastSlowSyncCompletionInstant() } returns Unit
        }.let {
            this to MLSClientManagerImpl(
                clientIdProvider,
                isAllowedToRegisterMLSClient,
                syncStateObserver,
                lazy { slowSyncRepository },
                lazy { clientRepository },
                lazy { registerMLSClient },
                testScope
            )
        }
    }
}
