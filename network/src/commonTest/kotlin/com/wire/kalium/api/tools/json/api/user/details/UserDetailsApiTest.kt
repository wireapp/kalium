package com.wire.kalium.api.tools.json.api.user.details

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.QualifiedHandleSample
import com.wire.kalium.api.tools.json.model.QualifiedIDSamples
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsApiImpl
import com.wire.kalium.network.api.user.details.qualifiedHandles
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

@ExperimentalCoroutinesApi
class UserDetailsApiTest : ApiTest {

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedIds(listOf(QualifiedIDSamples.one, QualifiedIDSamples.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val networkClient = mockAuthenticatedNetworkClient(
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
        val userDetailsApi: UserDetailsApi = UserDetailsApiImpl(networkClient)

        userDetailsApi.getMultipleUsers(params)
    }

    @Test
    fun givenListOfQualifiedHandles_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedHandles(listOf(QualifiedHandleSample.one, QualifiedHandleSample.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val networkClient = mockAuthenticatedNetworkClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImpl(networkClient)

        userDetailsApi.getMultipleUsers(params)
    }

    @Test
    fun givenAValidRequest_whenGettingListOfUsers_thenCorrectHttpHeadersAndMethodShouldBeUsed() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ListUsersRequestJson.validIdsJsonProvider.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiImpl(networkClient)

        userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(listOf()))
    }


    private companion object {
        const val PATH_LIST_USERS = "/list-users"
    }
}
