/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.api.v13

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.QualifiedIDSamples
import com.wire.kalium.mocks.responses.ListUsersResponseJson
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.api.v12.authenticated.UserDetailsApiV12
import com.wire.kalium.network.api.v13.authenticated.UserDetailsApiV13
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class MLSMessageApiV13Test : ApiTest() {

    @Test
    fun givenAUserId_whenInvokingUserInfo_thenShouldConfigureTheRequestOkAndReturnAResultWithData() = runTest {
        val httpClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.invoke(UserTypeDTO.REGULAR).rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertJson()
                assertPathEqual("${PATH_USERS}/${QualifiedIDSamples.one.domain}/${QualifiedIDSamples.one.value}")
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV13(httpClient)
        val result = userDetailsApi.getUserInfo(QualifiedIDSamples.one)
        assertTrue(result.isSuccessful())
        assertNotNull(result.value.type)
        assertEquals(UserTypeDTO.REGULAR, result.value.type)
    }

    @Test
    fun givenAUserId_whenInvokingUserInfoAndApp_thenShouldConfigureTheRequestOkAndReturnAResultWithData() = runTest {
        val httpClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.invoke(UserTypeDTO.APP).rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertJson()
                assertPathEqual("${PATH_USERS}/${QualifiedIDSamples.one.domain}/${QualifiedIDSamples.one.value}")
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV12(httpClient)
        val result = userDetailsApi.getUserInfo(QualifiedIDSamples.one)
        assertTrue(result.isSuccessful())
        assertNotNull(result.value.type)
        assertEquals(UserTypeDTO.APP, result.value.type)
    }

    @Test
    fun givenAUserId_whenInvokingUserInfoAndBot_thenShouldConfigureTheRequestOkAndReturnAResultWithData() = runTest {
        val httpClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.invoke(UserTypeDTO.BOT).rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertJson()
                assertPathEqual("${PATH_USERS}/${QualifiedIDSamples.one.domain}/${QualifiedIDSamples.one.value}")
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV12(httpClient)
        val result = userDetailsApi.getUserInfo(QualifiedIDSamples.one)
        assertTrue(result.isSuccessful())
        assertNotNull(result.value.type)
        assertEquals(UserTypeDTO.BOT, result.value.type)
    }


    @Test
    fun givenAUserId_whenInvokingUserInfoNull_thenShouldConfigureTheRequestOkAndReturnAResultWithData() = runTest {
        val httpClient = mockAuthenticatedNetworkClient(
            SUCCESS_RESPONSE.invoke(null).rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertJson()
                assertPathEqual("${PATH_USERS}/${QualifiedIDSamples.one.domain}/${QualifiedIDSamples.one.value}")
            }
        )
        val userDetailsApi: UserDetailsApi = UserDetailsApiV12(httpClient)
        val result = userDetailsApi.getUserInfo(QualifiedIDSamples.one)
        assertTrue(result.isSuccessful())
    }

    private companion object {
        const val PATH_USERS = "/users"
        val SUCCESS_RESPONSE = ListUsersResponseJson.v13
    }
}

