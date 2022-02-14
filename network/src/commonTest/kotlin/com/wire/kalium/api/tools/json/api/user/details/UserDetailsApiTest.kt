package com.wire.kalium.api.tools.json.api.user.details

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.QualifiedHandleSample
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.QualifiedHandle
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.QualifiedHandleListRequest
import com.wire.kalium.network.api.user.details.QualifiedUserIdListRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsApiImp
import com.wire.kalium.network.api.user.details.qualifiedHandles
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class UserDetailsApiTest : ApiTest {

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedIds(listOf(QualifiedIDSamples.one, QualifiedIDSamples.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val httpClient = mockAuthenticatedHttpClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
                assertBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImp(httpClient)

        userDetailsApi.getMultipleUsers(params)
    }

    @Test
    fun givenListOfMoreThan64QualifiedIds_whenGettingListOfUsers_thenRequestsShouldBeSplitIntoChunks() = runTest {
        val chunksCount = 3
        val params = getIdsList(chunksCount)
        var callCount = 0
        val httpClient = mockAuthenticatedHttpClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                callCount++
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImp(httpClient)

        userDetailsApi.getMultipleUsers(params)
        assertEquals(chunksCount, callCount)
    }


    @Test
    fun givenListOfQualifiedHandles_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedHandles(listOf(QualifiedHandleSample.one, QualifiedHandleSample.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val httpClient = mockAuthenticatedHttpClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImp(httpClient)

        userDetailsApi.getMultipleUsers(params)
    }

    @Test
    fun givenListOfMoreThan64QualifiedHandles_whenGettingListOfUsers_thenRequestsShouldBeSplitIntoChunks() = runTest {
        val chunksCount = 3
        val params = getHandlesList(chunksCount)
        var callCount = 0
        val httpClient = mockAuthenticatedHttpClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                callCount++
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImp(httpClient)

        userDetailsApi.getMultipleUsers(params)
        assertEquals(chunksCount, callCount)
    }

    @Test
    fun givenAValidRequest_whenGettingListOfUsers_thenCorrectHttpHeadersAndMethodShouldBeUsed() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImp(httpClient)

        userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(listOf()))
    }

    private fun getIdsList(chunksCount: Int): QualifiedUserIdListRequest {
        val idsList = mutableListOf<QualifiedID>()
        repeat(MAX_QUERY_SIZE * chunksCount) {
            idsList += QualifiedIDSamples.one.copy(domain = it.toString())
        }
        val params = ListUserRequest.qualifiedIds(idsList)
        return params
    }

    private fun getHandlesList(chunksCount: Int): QualifiedHandleListRequest {
        val handlesList = mutableListOf<QualifiedHandle>()
        repeat(MAX_QUERY_SIZE * chunksCount) {
            handlesList += QualifiedHandleSample.one.copy(domain = it.toString())
        }
        val params = ListUserRequest.qualifiedHandles(handlesList)
        return params
    }

    private companion object {
        const val PATH_LIST_USERS = "/list-users"
        const val MAX_QUERY_SIZE = 64
    }
}
