/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.ClientDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientsOfUsersResponse
import com.wire.kalium.network.api.base.authenticated.client.ListClientsOfUsersRequest
import com.wire.kalium.network.api.base.authenticated.client.PasswordRequest
import com.wire.kalium.network.api.base.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.client.UpdateClientRequest
import com.wire.kalium.network.api.base.model.PushTokenBody
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class ClientApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : ClientApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientDTO> =
        wrapKaliumResponse {
            httpClient.post(PATH_CLIENTS) {
                setBody(registerClientRequest)
            }
        }

    override suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>> =
        wrapKaliumResponse<ClientsOfUsersResponse> {
            httpClient.post("$PATH_USERS/$PATH_LIST_CLIENTS/v2") {
                setBody(ListClientsOfUsersRequest(userIds))
            }
        }.mapSuccess { response ->
            // Maps from nested mapping of domain -> Id -> List<Clients>, to Map of UserId to List<Clients>
            response.qualifiedMap.entries.flatMap { domainEntry ->
                domainEntry.value.map { userEntry ->
                    val userClients = userEntry.value
                    QualifiedID(userEntry.key, domainEntry.key) to userClients
                }
            }.toMap()
        }

    override suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientDTO>> =
        wrapKaliumResponse { httpClient.get(PATH_CLIENTS) }

    override suspend fun deleteClient(password: String?, clientID: String) =
        wrapKaliumResponse<Unit> {
            httpClient.delete("$PATH_CLIENTS/$clientID") {
                setBody(PasswordRequest(password))
            }
        }

    override suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientDTO> =
        wrapKaliumResponse { httpClient.get("$PATH_CLIENTS/$clientID") }

    override suspend fun updateClient(updateClientRequest: UpdateClientRequest, clientID: String): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put("$PATH_CLIENTS/$clientID") {
                setBody(updateClientRequest)
            }
        }

    override suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post(PUSH_TOKEN) {
            setBody(body)
        }
    }

    override suspend fun deregisterToken(pid: String): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.delete("$PUSH_TOKEN/$pid")
    }

    protected companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_LIST_CLIENTS = "list-clients"
        const val PUSH_TOKEN = "push/tokens"

    }
}
