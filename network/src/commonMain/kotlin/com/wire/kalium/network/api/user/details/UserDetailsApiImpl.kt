package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class UserDetailsApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : UserDetailsApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<List<UserProfileDTO>> {
        return wrapKaliumResponse {
            httpClient.post(PATH_LIST_USERS) {
                setBody(users)
            }
        }
    }

    override suspend fun getUserInfo(userId: UserId): NetworkResponse<UserProfileDTO> {
        return wrapKaliumResponse {
            httpClient.get("$PATH_USERS/${userId.domain}/${userId.value}")
        }
    }

    private companion object {
        const val PATH_LIST_USERS = "list-users"
        const val PATH_USERS = "users"
    }
}
