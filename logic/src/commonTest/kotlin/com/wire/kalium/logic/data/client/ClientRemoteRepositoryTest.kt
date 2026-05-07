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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.model.PushTokenBody
import com.wire.kalium.network.api.model.UserId as UserIdDTO
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
import kotlinx.coroutines.test.runTest

class ClientRemoteRepositoryTest {

    @Test
    fun givenClientCapabilitiesParam_whenUpdatingCapabilities_thenInvokeClientApiOnce() = runTest {
        val (arrangement, clientRepository) = Arrangement()
            .withClientUpdateClientCapabilities()
            .arrange()

        clientRepository.updateClientCapabilities(
            UpdateClientCapabilitiesParam(listOf(ClientCapability.LegalHoldImplicitConsent)),
            "client-id"
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientApi.updateClientCapabilities(any(), any())
        }
    }

    @Test
    fun givenValidParams_whenRegisteringPushToken_thenShouldSucceed() = runTest {

        val (arrangement, clientRemoteRepository) = Arrangement()
            .withRegisterToken(NetworkResponse.Success(Unit, mapOf(), 200))
            .arrange()
        val actual = clientRemoteRepository.registerToken(pushTokenRequestBody)

        actual.shouldSucceed {
            assertEquals(Unit, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientApi.registerToken(any())
        }

    }

    @Test
    fun givenOtherUsersClientsSuccess_whenFetchingOtherUserClients_thenTheSuccessIsReturned() =
        runTest {
            // Given
            val userId = UserId("123", "wire.com")
            val userIdDto = UserIdDTO("123", "wire.com")
            val otherUsersClients = listOf(
                SimpleClientResponse(deviceClass = DeviceTypeDTO.Phone, id = "1111"),
                SimpleClientResponse(deviceClass = DeviceTypeDTO.Desktop, id = "2222")
            )

            val expectedSuccess = Either.Right(listOf(userId to otherUsersClients))
            val (arrangement, clientRepository) = Arrangement().withSuccessfulResponse(
                mapOf(
                    userIdDto to otherUsersClients
                )
            ).arrange()

            // When
            val result = clientRepository.fetchOtherUserClients(listOf(userId))

            // Then
            result.shouldSucceed { expectedSuccess.value }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientApi.listClientsOfUsers(any())
            }
        }

    @Test
    fun givenOtherUsersClientsError_whenFetchingOtherUserClients_thenTheErrorIsPropagated() =
        runTest {
            // Given
            val userId = UserId("123", "wire.com")
            val notFound = TestNetworkException.noTeam
            val (arrangement, clientRepository) = Arrangement()
                .withErrorResponse(notFound).arrange()

            // When
            val result = clientRepository.fetchOtherUserClients(listOf(userId))

            // Then
            result.shouldFail { Either.Left(notFound).value }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientApi.listClientsOfUsers(any())
            }
        }

    private companion object {
        val pushTokenRequestBody = PushTokenBody(
            senderId = "7239",
            client = "cliId", token = "7239", transport = "GCM"
        )
    }

    private class Arrangement {

        val clientApi = mock<ClientApi>(mode = MockMode.autoUnit)
        val clientConfig = mock<ClientConfig>(mode = MockMode.autoUnit)

        var clientRepository: ClientRemoteRepository =
            ClientRemoteDataSource(clientApi, clientConfig)

        suspend fun withRegisterToken(result: NetworkResponse<Unit>) = apply {
            everySuspend {
                clientApi.registerToken(any())
            }.returns(result)
        }

        suspend fun withClientUpdateClientCapabilities() = apply {
            everySuspend {
                clientApi.updateClientCapabilities(any(), any())
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withSuccessfulResponse(expectedResponse: Map<UserIdDTO, List<SimpleClientResponse>>) =
            apply {
                everySuspend {
                    clientApi.listClientsOfUsers(any())
                }.returns(NetworkResponse.Success(expectedResponse, mapOf(), 200))
            }

        suspend fun withErrorResponse(kaliumException: KaliumException) = apply {
            everySuspend {
                clientApi.listClientsOfUsers(any())
            }.returns(
                NetworkResponse.Error(
                    kaliumException
                )
            )
        }

        fun arrange() = this to clientRepository
    }
}
