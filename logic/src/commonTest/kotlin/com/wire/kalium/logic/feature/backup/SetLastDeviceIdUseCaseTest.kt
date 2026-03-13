/** Wire
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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetLastDeviceIdUseCaseTest {

    @Test
    fun givenClientIdFetchFails_whenInvoked_thenReturnFailure() = runTest {
        val error = CoreFailure.Unknown(Exception("Client fetch failed"))
        val arrangement = Arrangement()
            .withCurrentClientIdProviderFailure(error)
        val useCase = arrangement.useCase

        val result = useCase()

        assertIs<SetLastDeviceIdResult.Failure>(result)
        assertEquals(error, (result as SetLastDeviceIdResult.Failure).error)
    }

    @Test
    fun givenRemoteRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val clientId = "client-id-123"
        val error = NetworkFailure.NoNetworkConnection(Exception("Remote operation failed"))
        val arrangement = Arrangement()
            .withCurrentClientIdProviderSuccess(clientId)
            .withSetLastDeviceIdFailure(error)
        val useCase = arrangement.useCase

        val result = useCase()

        assertIs<SetLastDeviceIdResult.Failure>(result)
        assertEquals(error, result.error)
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(
                deviceId = clientId
            )
        }
    }

    @Test
    fun givenValidClientIdAndRemoteRepositorySucceeds_whenInvoked_thenReturnSuccess() = runTest {
        val clientId = "client-id-123"
        val arrangement = Arrangement()
            .withCurrentClientIdProviderSuccess(clientId)
            .withSetLastDeviceIdSuccess()
        val useCase = arrangement.useCase

        val result = useCase()

        assertIs<SetLastDeviceIdResult.Success>(result)
        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(
                deviceId = clientId
            )
        }
    }

    @Test
    fun givenValidClientId_whenInvoked_thenRepositoryCalledWithCorrectParameters() = runTest {
        val clientId = "device-xyz"
        val userId = UserId("user-id-456", "domain.com")
        val arrangement = Arrangement(userId = userId)
            .withCurrentClientIdProviderSuccess(clientId)
            .withSetLastDeviceIdSuccess()
        val useCase = arrangement.useCase

        useCase()

        verifySuspend(VerifyMode.atMost(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(
                deviceId = clientId
            )
        }
    }

    private class Arrangement(
        val userId: UserId = UserId("test-user-id", "test-domain.com")
    ) {
        val currentClientIdProvider = mock<CurrentClientIdProvider>()

        val cryptoStateBackupRemoteRepository = mock<CryptoStateBackupRemoteRepository>()

        val useCase: SetLastDeviceIdUseCase by lazy {
            SetLastDeviceIdUseCaseImpl(
                currentClientIdProvider = currentClientIdProvider,
                cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository
            )
        }

        fun withCurrentClientIdProviderSuccess(clientId: String) = apply {
            everySuspend {
                currentClientIdProvider()
            }.returns(Either.Right(ClientId(clientId)))
        }

        fun withCurrentClientIdProviderFailure(error: CoreFailure) = apply {
            everySuspend {
                currentClientIdProvider()
            }.returns(Either.Left(error))
        }

        fun withSetLastDeviceIdSuccess() = apply {
            everySuspend {
                cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = any())
            }.returns(Either.Right(Unit))
        }

        fun withSetLastDeviceIdFailure(error: NetworkFailure) = apply {
            everySuspend {
                cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = any())
            }.returns(Either.Left(error))
        }
    }
}
