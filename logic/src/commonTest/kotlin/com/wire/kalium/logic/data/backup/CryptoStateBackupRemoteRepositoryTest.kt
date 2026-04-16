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
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        coVerify { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }.wasInvoked(exactly = once)
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
        coVerify { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiReturnsUserNotFound_whenDownloadingCryptoState_thenReturnNoCryptoStateAvailable() = runTest {
        val userNotFoundError = KaliumException.InvalidRequestError(
            ErrorResponse(401, "user not found", "user_not_found")
        )
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(userNotFoundError))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<BackupFailure.NoCryptoStateAvailable>(failure)
        }
        coVerify { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiReturnsNoCryptoState_whenDownloadingCryptoState_thenReturnNoCryptoStateAvailable() = runTest {
        val noCryptoStateError = KaliumException.InvalidRequestError(
            ErrorResponse(403, "no crypto state", "no_crypto_state")
        )
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(noCryptoStateError))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<BackupFailure.NoCryptoStateAvailable>(failure)
        }
        coVerify { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiReturnsSuccessWithZeroContentLength_whenDownloadingCryptoState_thenReturnNoCryptoStateAvailable() = runTest {
        val emptyResponseError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(200, "Empty response body", "no_crypto_state")
        )
        val (arrangement, repository) = Arrangement()
            .withDownloadCryptoState(NetworkResponse.Error(emptyResponseError))
            .arrange()

        val result = repository.downloadCryptoState(Buffer())

        result.shouldFail { failure ->
            assertIs<BackupFailure.NoCryptoStateAvailable>(failure)
        }
        coVerify { arrangement.nomadDeviceSyncApi.downloadCryptoState(any()) }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val nomadDeviceSyncApi = mock(NomadDeviceSyncApi::class)
        private val repository = CryptoStateBackupRemoteDataSource(nomadDeviceSyncApi)

        suspend fun withDownloadCryptoState(result: NetworkResponse<Unit>) = apply {
            coEvery { nomadDeviceSyncApi.downloadCryptoState(any()) }.returns(result)
        }

        fun arrange() = this to repository
    }

}
