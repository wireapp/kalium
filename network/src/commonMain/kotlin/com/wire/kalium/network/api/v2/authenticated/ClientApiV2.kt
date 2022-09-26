package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.client.ClientsOfUsersResponse
import com.wire.kalium.network.api.base.authenticated.client.ListClientsOfUsersRequest
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v0.authenticated.ClientApiV0
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class ClientApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ClientApiV0(authenticatedNetworkClient) {
    override suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>> =
        wrapKaliumResponse<ClientsOfUsersResponse> {
            httpClient.post("$PATH_USERS/$PATH_List_CLIENTS") {
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
}
