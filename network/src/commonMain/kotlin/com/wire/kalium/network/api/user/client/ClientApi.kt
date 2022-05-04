package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

interface ClientApi {

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientResponse>

    suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>>

    suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientResponse>>

    suspend fun deleteClient(password: String, clientID: String): NetworkResponse<Unit>

    suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientResponse>

    suspend fun updateClient(updateClientRequest: UpdateClientRequest, clientID: String): NetworkResponse<Unit>

    suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit>
}


class ClientApiImpl(private val httpClient: HttpClient) : ClientApi {
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

    override suspend fun deleteClient(password: String, clientID: String) =
        wrapKaliumResponse<Unit> {
            httpClient.delete("$PATH_CLIENTS/$clientID") {
                setBody(PasswordRequest(password))
            }
        }

    override suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientResponse> =
        wrapKaliumResponse { httpClient.get("$PATH_CLIENTS/$clientID") }

    override suspend fun updateClient(updateClientRequest: UpdateClientRequest, clientID: String): NetworkResponse<Unit> =
        wrapKaliumResponse { httpClient.put("$PATH_CLIENTS/$clientID") {
            setBody(updateClientRequest)
        } }

    override suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.post(PUSH_TOKEN) {
            setBody(body)
        }
    }

    private companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_LIST_CLIENTS = "$PATH_USERS/list-clients/v2"
        const val PUSH_TOKEN = "push/tokens"

    }
}
