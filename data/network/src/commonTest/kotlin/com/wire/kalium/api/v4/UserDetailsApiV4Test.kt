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
package com.wire.kalium.api.v4

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.QualifiedIDSamples
import com.wire.kalium.mocks.responses.ListUsersResponseJson
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.v4.authenticated.UserDetailsApiV4
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
internal class UserDetailsApiV4Test : ApiTest() {

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsersWithFailedUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedIds(
            listOf(QualifiedIDSamples.one, QualifiedIDSamples.two, QualifiedIDSamples.three)
        )
        val expectedRequestBody = KtxSerializer.json.encodeToString(params)
        val networkClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE_WITH_FAILED_TO_LIST.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual(PATH_LIST_USERS)
                assertJsonBodyContent(expectedRequestBody)
            }
        )
        val userDetailsApi: UserDetailsApiV4 = UserDetailsApiV4(networkClient)

        val response: NetworkResponse<ListUsersDTO> = userDetailsApi.getMultipleUsers(params)
        assertTrue(response.isSuccessful())
        assertTrue(response.value.usersFailed.isNotEmpty())
        assertEquals(response.value.usersFound, SUCCESS_RESPONSE_WITH_FAILED_TO_LIST.serializableData.usersFound)
    }

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        val params = ListUserRequest.qualifiedIds(
            listOf(QualifiedIDSamples.one, QualifiedIDSamples.two, QualifiedIDSamples.three)
        )
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
        val userDetailsApi: UserDetailsApiV4 = UserDetailsApiV4(networkClient)

        val response: NetworkResponse<ListUsersDTO> = userDetailsApi.getMultipleUsers(params)
        assertTrue(response.isSuccessful())
        assertTrue(response.value.usersFailed.isEmpty())
        assertEquals(response.value.usersFound, SUCCESS_RESPONSE.serializableData.usersFound)
    }

    private companion object {
        const val PATH_LIST_USERS = "/list-users"
        val SUCCESS_RESPONSE_WITH_FAILED_TO_LIST = ListUsersResponseJson.v4_withFailedUsers
        val SUCCESS_RESPONSE = ListUsersResponseJson.v4
    }
}
