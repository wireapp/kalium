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
package com.wire.kalium.api.v8

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.requests.DomainRegistrationRequestJson
import com.wire.kalium.mocks.responses.DomainRegistrationResponseJson
import com.wire.kalium.network.api.v8.unauthenticated.GetDomainRegistrationApiV8
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GetDomainRegistrationApiV8Test : ApiTest() {

    @Test
    fun whenCallingGetDomainRegistration_thenTheRequestShouldBeConfiguredOK() = runTest {
        val emailToVerify = "test@wire.com"
        val networkClient = mockUnauthenticatedNetworkClient(
            SUCCESS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("get-domain-registration")
                assertJsonBodyContent(DomainRegistrationRequestJson.createValid(emailToVerify).rawJson)
            }
        )

        GetDomainRegistrationApiV8(networkClient).getDomainRegistration(emailToVerify)
    }

    @Test
    fun whenCallingGetDomainRegistration_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val emailToVerify = "test@wire.com"
        val networkClient = mockUnauthenticatedNetworkClient(
            SUCCESS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("get-domain-registration")
                assertJsonBodyContent(DomainRegistrationRequestJson.createValid(emailToVerify).rawJson)
            }
        )

        val response = GetDomainRegistrationApiV8(networkClient).getDomainRegistration(emailToVerify)
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenAnInvalidDomain_whenCallingGetDomainRegistration_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val emailToVerify = "test@wire.com"
        val networkClient = mockUnauthenticatedNetworkClient(
            INVALID_DOMAIN_RESPONSE,
            statusCode = HttpStatusCode.BadRequest,
            assertion = {
                assertPost()
                assertPathEqual("get-domain-registration")
                assertJsonBodyContent(DomainRegistrationRequestJson.createValid(emailToVerify).rawJson)
            }
        )

        val response = GetDomainRegistrationApiV8(networkClient).getDomainRegistration(emailToVerify)
        assertFalse(response.isSuccessful())
    }

    companion object {
        val SUCCESS_RESPONSE = DomainRegistrationResponseJson.success.rawJson
        val INVALID_DOMAIN_RESPONSE = DomainRegistrationResponseJson.invalidDomain.rawJson
    }
}
