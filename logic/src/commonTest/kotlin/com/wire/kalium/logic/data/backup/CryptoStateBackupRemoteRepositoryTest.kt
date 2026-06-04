/*
 * Wire
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
package com.wire.kalium.logic.data.backup

import com.wire.kalium.common.error.BackupFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import okio.Buffer

class CryptoStateBackupRemoteRepositoryTest {

    @Test
    fun givenApiSuccess_whenDownloadingCryptoState_thenReturnUnit() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Success(Unit, mapOf(), 200))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldSucceed { actual ->
            assertEquals(Unit, actual)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }
    }

    @Test
    fun givenApiFailure_whenDownloadingCryptoState_thenReturnFailure() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(TestNetworkException.generic))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<NetworkFailure.ServerMiscommunication>(failure)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }
    }

    @Test
    fun givenApiReturnsUserNotFound_whenDownloadingCryptoState_thenReturnNoCryptoStateAvailable() = runTest {
        val userNotFoundError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(401, "user not found", "user_not_found")
        )
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(userNotFoundError))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<BackupFailure.NoCryptoStateAvailable>(failure)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }
    }

    @Test
    fun givenApiReturnsNoCryptoState_whenDownloadingCryptoState_thenReturnNoCryptoStateAvailable() = runTest {
        val noCryptoStateError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(403, "no crypto state", "no_crypto_state")
        )
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(noCryptoStateError))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<BackupFailure.NoCryptoStateAvailable>(failure)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }
    }

    private class Arrangement {
        val nomadDeviceSyncApi = mock<NomadDeviceSyncApi>(mode = MockMode.autoUnit)
        private val repository = CryptoStateBackupRemoteDataSource(nomadDeviceSyncApi)

        suspend fun withDownloadCryptoState(result: NetworkResponse<Unit>) = apply {
            everySuspend { nomadDeviceSyncApi.downloadCryptoState(any()) }.returns(result)
        }

        fun arrange() = this to repository
    }

}
