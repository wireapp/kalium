package com.wire.kalium.network.api.prekey

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.message.UserIdToClientMap
import com.wire.kalium.network.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse

class PreKeyApiImpl(private val httpClient: HttpClient) : PreKeyApi {
    override suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<UserClientsToPreKeyMap> = wrapKaliumResponse {
        httpClient.post<HttpResponse>(path = "$PATH_USERS$PATH_PRE_KEY") {
            body = users
        }.receive()
    }

    override suspend fun getUsersPreKey(users: DomainToUserIdToClientsMap): KaliumHttpResult<DomainToUserIdToClientsToPreykeyMap> =
        wrapKaliumResponse {
            httpClient.post<HttpResponse>(path = "$PATH_USERS$PATH_List_PREKEYS") {
                body = users
            }.receive()
        }

    override suspend fun getClientAvailablePrekeys(clientId: String): KaliumHttpResult<List<Int>> = wrapKaliumResponse {
        httpClient.get<HttpResponse>(path = "$PATH_CLIENTS/$clientId$PATH_PRE_KEY").receive()
    }

    private companion object {
        const val PATH_USERS = "/users"
        const val PATH_CLIENTS = "/clients"
        const val PATH_PRE_KEY = "/prekeys"
        const val PATH_List_PREKEYS = "/list-prekeys"
    }
}

