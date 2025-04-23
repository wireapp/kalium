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

package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.client.UpdateClientCapabilitiesParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.signatureAlgorithm
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.authenticated.client.UpdateClientMlsPublicKeysRequest
import com.wire.kalium.network.api.model.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mockable
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@Mockable
interface ClientRemoteRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client>
    suspend fun registerMLSClient(
        clientId: ClientId,
        publicKey: String,
        cipherSuite: CipherSuite
    ): Either<NetworkFailure, Unit>

    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit>
    suspend fun deregisterToken(pid: String): Either<NetworkFailure, Unit>
    suspend fun fetchOtherUserClients(
        userIdList: List<UserId>
    ): Either<NetworkFailure, Map<UserIdDTO, List<SimpleClientResponse>>>

    suspend fun updateClientCapabilities(
        updateClientCapabilitiesParam: UpdateClientCapabilitiesParam,
        clientID: String
    ): Either<NetworkFailure, Unit>
}

class ClientRemoteDataSource(
    private val clientApi: ClientApi,
    private val clientConfig: ClientConfig,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
) : ClientRemoteRepository {

    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> =
        wrapApiRequest { clientApi.registerClient(clientMapper.toRegisterClientRequest(clientConfig, param)) }
            .map { clientResponse -> clientMapper.fromClientDto(clientResponse) }

    override suspend fun registerMLSClient(
        clientId: ClientId,
        publicKey: String,
        cipherSuite: CipherSuite
    ): Either<NetworkFailure, Unit> = cipherSuite.signatureAlgorithm()?.let { signatureAlgorithm ->
        wrapApiRequest {
            clientApi.updateClientMlsPublicKeys(
                UpdateClientMlsPublicKeysRequest(
                    mapOf(signatureAlgorithm to publicKey),
                ),
                clientId.value
            )
        }
    } ?: NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IllegalArgumentException("Unknown cipher suite"))).left()

    override suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit> =
        wrapApiRequest { clientApi.deleteClient(param.password, param.clientId.value) }

    override suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit> = wrapApiRequest {
        clientApi.registerToken(body)
    }

    override suspend fun deregisterToken(pid: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        clientApi.deregisterToken(pid)
    }

    override suspend fun fetchOtherUserClients(
        userIdList: List<UserId>
    ): Either<NetworkFailure, Map<UserIdDTO, List<SimpleClientResponse>>> {
        val networkUserId = userIdList.map { it.toApi() }
        return wrapApiRequest { clientApi.listClientsOfUsers(networkUserId) }
    }

    override suspend fun updateClientCapabilities(
        updateClientCapabilitiesParam: UpdateClientCapabilitiesParam,
        clientID: String
    ): Either<NetworkFailure, Unit> = wrapApiRequest {
        clientApi.updateClientCapabilities(
            clientMapper.toUpdateClientCapabilitiesRequest(updateClientCapabilitiesParam),
            clientID
        )
    }
}
