package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class UserDetailsApiImp(private val httpClient: HttpClient) : UserDetailsApi {

    override suspend fun getMultipleUsers(users: ListUserRequest): NetworkResponse<List<UserDetailsResponse>> {
        val chunkedRequests = separateRequestInChunks(users)
        val results = performChunkedRequests(chunkedRequests)

        // If any request fails, abort and return the failure
        results.firstOrNull { !it.isSuccessful() }?.let { errorResponse ->
            return errorResponse as NetworkResponse.Error
        }

        // Join all the successful results together into a single one
        return results.fold(results.first()) { acc, item ->
            item as NetworkResponse.Success
            acc.mapSuccess { currentValue ->
                currentValue + item.value
            }
        }
    }

    private suspend fun performChunkedRequests(chunkedRequests: List<ListUserRequest>): List<NetworkResponse<List<UserDetailsResponse>>> =
        coroutineScope {
            chunkedRequests.map { chunkedIds ->
                async {
                    wrapKaliumResponse<List<UserDetailsResponse>> {
                        httpClient.post(PATH_LIST_USERS) {
                            setBody(chunkedIds)
                        }
                    }
                }
            }.awaitAll()
        }

    private fun separateRequestInChunks(users: ListUserRequest) =
        if (users is QualifiedHandleListRequest) {
            separateHandlesRequest(users)
        } else {
            separateUserIdsRequest(users)
        }

    private fun separateHandlesRequest(users: QualifiedHandleListRequest) =
        users.qualifiedHandles.chunked(MAX_USERS_PER_REQUEST).map { chunk ->
            QualifiedHandleListRequest(chunk)
        }

    private fun separateUserIdsRequest(users: ListUserRequest): List<QualifiedUserIdListRequest> {
        users as QualifiedUserIdListRequest
        return users.qualifiedIds.chunked(MAX_USERS_PER_REQUEST).map { chunk ->
            QualifiedUserIdListRequest(chunk)
        }
    }

    private companion object {
        const val MAX_USERS_PER_REQUEST = 64
        const val PATH_LIST_USERS = "list-users"
    }
}
