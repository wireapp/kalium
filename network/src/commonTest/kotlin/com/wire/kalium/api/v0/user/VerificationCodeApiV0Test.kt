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
package com.wire.kalium.api.v0.user

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.unauthenticated.verification.VerificationCodeApi
import com.wire.kalium.network.api.v0.unauthenticated.VerificationCodeApiV0
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class VerificationCodeApiV0Test : ApiTest() {

    @Test
    fun givenASendCodeRequest_whenExecutingIt_thenThePathShouldBeCorrect() = runTest {
        val client = mockUnauthenticatedNetworkClient("", HttpStatusCode.OK, {
            assertPathEqual(VerificationCodeApiV0.PATH_VERIFICATION_CODE_SEND)
        })

        val api = VerificationCodeApiV0(client)

        api.sendVerificationCode("testEmail", VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION)
    }

    @Test
    fun givenASendCodeRequest_whenExecutingIt_thenTheHttpMethodShouldBePost() = runTest {
        val client = mockUnauthenticatedNetworkClient("", HttpStatusCode.OK, {
            assertPost()
        })

        val api = VerificationCodeApiV0(client)

        api.sendVerificationCode("testEmail", VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION)
    }

    @Test
    fun givenASendCodeRequest_whenExecutingIt_thenTheBodyShouldBeSerializedCorrectly() = runTest {
        val testEmail = "user@example.org"
        val client = mockUnauthenticatedNetworkClient("", HttpStatusCode.OK, {
            assertJsonBodyContent(
                """
                |{
                |  "action": "login",
                |  "email": "$testEmail"
                |}
                """.trimMargin()
            )
        })

        val api = VerificationCodeApiV0(client)

        api.sendVerificationCode(testEmail, VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION)
    }

    @Test
    fun givenASendCodeRequestSucceeds_whenExecutingIt_thenTheSuccessShouldBePropagated() = runTest {
        val client = mockUnauthenticatedNetworkClient("", HttpStatusCode.OK)

        val api = VerificationCodeApiV0(client)

        val result = api.sendVerificationCode(
            email = "user_example",
            action = VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION
        )
        assertTrue(result.isSuccessful())
    }

    @Test
    fun givenASendCodeRequestFails_whenExecutingIt_thenTheFailureShouldBePropagated() = runTest {
        val client = mockUnauthenticatedNetworkClient("", HttpStatusCode.BadRequest)

        val api = VerificationCodeApiV0(client)

        val result = api.sendVerificationCode(
            email = "user_example",
            action = VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION
        )
        assertFalse(result.isSuccessful())
    }
}
