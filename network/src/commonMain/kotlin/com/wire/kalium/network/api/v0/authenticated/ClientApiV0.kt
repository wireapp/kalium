package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.ClientResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientsOfUsersResponse
import com.wire.kalium.network.api.base.authenticated.client.ListClientsOfUsersRequest
import com.wire.kalium.network.api.base.authenticated.client.PasswordRequest
import com.wire.kalium.network.api.base.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.client.UpdateClientRequest
import com.wire.kalium.network.api.base.model.PushTokenBody
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal class ClientApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : ClientApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_CLIENTS) {
                setBody(registerClientRequest)
            }
        }

    override suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>> =
        wrapKaliumResponse<ClientsOfUsersResponse> {
            httpClient.post(PATH_LIST_CLIENTS) {
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

    override suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientResponse>> =
        wrapKaliumResponse { httpClient.get(PATH_CLIENTS) }

    override suspend fun deleteClient(password: String?, clientID: String) =
        wrapKaliumResponse<Unit> {
            httpClient.delete("$PATH_CLIENTS/$clientID") {
                setBody(PasswordRequest(password))
            }
        }

    override suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientResponse> =
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

    private companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_LIST_CLIENTS = "$PATH_USERS/list-clients/v2"
        const val PUSH_TOKEN = "push/tokens"

    }
}
