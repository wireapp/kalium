package com.wire.kalium.network.api.prekey

import com.wire.kalium.network.api.message.UserIdToClientMap
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post

class PreKeyApiImpl(private val httpClient: HttpClient) : PreKeyApi {
    override suspend fun getUsersPreKey(users: UserIdToClientMap): NetworkResponse<UserClientsToPreKeyMap> = wrapKaliumResponse {
        httpClient.post(path = "$PATH_USERS$PATH_PRE_KEY") {
            body = users
        }
    }

    override suspend fun getUsersPreKey(users: DomainToUserIdToClientsMap): NetworkResponse<DomainToUserIdToClientsToPreykeyMap> =
        wrapKaliumResponse {
            httpClient.post(path = "$PATH_USERS$PATH_List_PREKEYS") {
                body = users
            }
        }

    override suspend fun getClientAvailablePrekeys(clientId: String): NetworkResponse<List<Int>> = wrapKaliumResponse {
        httpClient.get(path = "$PATH_CLIENTS/$clientId$PATH_PRE_KEY")
    }

    private companion object {
        const val PATH_USERS = "/users"
        const val PATH_CLIENTS = "/clients"
        const val PATH_PRE_KEY = "/prekeys"
        const val PATH_List_PREKEYS = "/list-prekeys"
    }
}

