package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface ClientApi {

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientResponse>

    suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>>

    suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientResponse>>

    suspend fun deleteClient(password: String, clientID: String): NetworkResponse<Unit>

    suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientResponse>
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
                    QualifiedID(domainEntry.key, userEntry.key) to userClients
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

    private companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_LIST_CLIENTS = "$PATH_USERS/$PATH_CLIENTS/list-clients/v2"
    }
}
