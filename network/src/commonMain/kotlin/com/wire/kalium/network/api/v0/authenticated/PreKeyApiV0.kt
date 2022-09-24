package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal class PreKeyApiV0 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : PreKeyApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getUsersPreKey(
        users: Map<String, Map<String, List<String>>>
    ): NetworkResponse<Map<String, Map<String, Map<String, PreKeyDTO?>>>
            > =
        wrapKaliumResponse {
            httpClient.post("$PATH_USERS/$PATH_List_PREKEYS") {
                setBody(users)
            }
        }

    override suspend fun getClientAvailablePrekeys(clientId: String): NetworkResponse<List<Int>> = wrapKaliumResponse {
        httpClient.get("$PATH_CLIENTS/$clientId/$PATH_PRE_KEY")
    }

    private companion object {
        const val PATH_USERS = "users"
        const val PATH_CLIENTS = "clients"
        const val PATH_PRE_KEY = "prekeys"
        const val PATH_List_PREKEYS = "list-prekeys"
    }
}
