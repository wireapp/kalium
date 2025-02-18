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
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class MLSClientManagerTest {

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsAllowedToRegisterMLSClient(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasNotInvoked()
        }

    @Test
    fun givenMLSClientIsNotRegistered_whenObservingSyncFinishes_thenMLSClientIsRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsAllowedToRegisterMLSClient(true)
                .withHasRegisteredMLSClient(Either.Right(false))
                .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
                .withRegisterMLSClientSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasInvoked(once)

            coVerify {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }.wasInvoked(once)
        }

    @Test
    fun givenMLSClientIsRegistered_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsAllowedToRegisterMLSClient(true)
                .withHasRegisteredMLSClient(Either.Right(true))
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            coVerify {
                arrangement.registerMLSClient.invoke(any())
            }.wasNotInvoked()
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        var slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        var clientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val isAllowedToRegisterMLSClient = mock(IsAllowedToRegisterMLSClientUseCase::class)

        @Mock
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

        fun arrange() = this to MLSClientManagerImpl(
            clientIdProvider,
            isAllowedToRegisterMLSClient,
            incrementalSyncRepository,
            lazy { slowSyncRepository },
            lazy { clientRepository },
            lazy { registerMLSClient },
            TestKaliumDispatcher
        )
    }
}
