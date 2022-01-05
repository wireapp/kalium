package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post

class ClientApiImp(private val httpClient: HttpClient) : ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<RegisterClientResponse> =
        wrapKaliumResponse {
            httpClient.post(path = PATH_CLIENTS) {
                body = registerClientRequest
            }
        }

    override suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>> =
        wrapKaliumResponse<ClientsOfUsersResponse> {
            httpClient.post(PATH_LIST_CLIENTS) {
                body = ListClientsOfUsersRequest(userIds)
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

    private companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_LIST_CLIENTS = "$PATH_USERS/$PATH_CLIENTS/list-clients/v2"
    }
}
