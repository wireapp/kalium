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
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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
            // todo: cover all cases
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

        fun arrange(testScope: TestScope) = this to MLSClientManager(
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
