package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class UserDetailsApiImp(private val httpClient: HttpClient) : UserDetailsApi {

    override suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<List<UserDetailsResponse>> {
        return wrapKaliumResponse {
            httpClient.post(PATH_LIST_USERS) {
                setBody(users)
            }
        }
    }

    private companion object {
        const val PATH_LIST_USERS = "list-users"
    }
}
