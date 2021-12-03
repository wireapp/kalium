package com.wire.kalium.api.prekey

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.message.UserIdToClientMap
import com.wire.kalium.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class PreKeyApiImpl(private val httpClient: HttpClient) : PreKeyApi {
    override suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<UserClientsToPreKeyMap> = wrapKaliumResponse {
        httpClient.post<HttpResponse>(path = "$PATH_USERS$PATH_PRE_KEY") {
            body = users
        }.receive()
    }

    private companion object {
        const val PATH_USERS = "/users"
        const val PATH_PRE_KEY = "/prekeys"
    }
}

