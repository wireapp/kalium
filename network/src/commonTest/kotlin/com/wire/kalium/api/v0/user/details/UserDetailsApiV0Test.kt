/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.api.v0.user.details

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.QualifiedHandleSample
import com.wire.kalium.api.json.model.QualifiedIDSamples
import com.wire.kalium.model.ListUsersResponseJson
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedHandles
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.v0.authenticated.UserDetailsApiV0
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class UserDetailsApiV0Test : ApiTest() {

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedIds(listOf(QualifiedIDSamples.one, QualifiedIDSamples.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val networkClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
                assertJsonBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV0(networkClient)

        val response: NetworkResponse<ListUsersDTO> = userDetailsApi.getMultipleUsers(params)
        assertTrue(response.isSuccessful())
        assertTrue(response.value.usersFailed.isEmpty())
        assertEquals(response.value.usersFound, SUCCESS_RESPONSE.serializableData)
    }

    @Test
    fun givenListOfQualifiedHandles_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedHandles(listOf(QualifiedHandleSample.one, QualifiedHandleSample.two))
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val networkClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertJsonBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV0(networkClient)

        userDetailsApi.getMultipleUsers(params)
    }

    @Test
    fun givenAValidRequest_whenGettingListOfUsers_thenCorrectHttpHeadersAndMethodShouldBeUsed() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV0(networkClient)

        userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(listOf()))
    }

    @Test
    fun givenAUserId_whenInvokingUserInfo_thenShouldConfigureTheRequestOkAndReturnAResultWithData() = runTest {
        val httpClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertJson()
                assertPathEqual("$PATH_USERS/${QualifiedIDSamples.one.domain}/${QualifiedIDSamples.one.value}")
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV0(httpClient)

        val result = userDetailsApi.getUserInfo(QualifiedIDSamples.one)
        result.isSuccessful()
    }

    private companion object {
        const val PATH_LIST_USERS = "/list-users"
        const val PATH_USERS = "/users"
        private val SUCCESS_RESPONSE = ListUsersResponseJson.v0
    }
}
