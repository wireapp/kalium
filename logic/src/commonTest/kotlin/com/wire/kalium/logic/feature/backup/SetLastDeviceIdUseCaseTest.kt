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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
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
    fun givenRemoteRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val clientId = "client-id-123"
        val error = NetworkFailure.NoNetworkConnection(Exception("Remote operation failed"))
        val arrangement = Arrangement()
            .withSetLastDeviceIdFailure(error)
        val useCase = arrangement.useCase

        val result = useCase(clientId)

        assertIs<SetLastDeviceIdResult.Failure>(result)
        assertEquals(error, result.error)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = clientId)
        }
    }

    @Test
    fun givenRemoteRepositorySucceeds_whenInvoked_thenReturnSuccess() = runTest {
        val clientId = "client-id-123"
        val arrangement = Arrangement()
            .withSetLastDeviceIdSuccess()
        val useCase = arrangement.useCase

        val result = useCase(clientId)

        assertIs<SetLastDeviceIdResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = clientId)
        }
    }

    @Test
    fun givenValidClientId_whenInvoked_thenRepositoryCalledWithCorrectParameters() = runTest {
        val clientId = "device-xyz"
        val arrangement = Arrangement()
            .withSetLastDeviceIdSuccess()
        val useCase = arrangement.useCase

        useCase(clientId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoStateBackupRemoteRepository.setLastDeviceId(deviceId = clientId)
        }
    }

    private class Arrangement {
        val cryptoStateBackupRemoteRepository = mock<CryptoStateBackupRemoteRepository>()

        val useCase: SetLastDeviceIdUseCase by lazy {
            SetLastDeviceIdUseCaseImpl(
                cryptoStateBackupRemoteRepository = cryptoStateBackupRemoteRepository
            )
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