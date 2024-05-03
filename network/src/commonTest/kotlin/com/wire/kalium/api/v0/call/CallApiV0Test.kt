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

package com.wire.kalium.api.v0.call

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.AnyResponseProvider
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.v0.authenticated.CallApiV0
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
internal class CallApiV0Test : ApiTest() {

    @Test
    fun givenCallApi_whenGettingCallConfigWithNoLimit_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_CONFIG.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("$PATH_CALLS/$PATH_CONFIG")
            }
        )

        val callApi: CallApi = CallApiV0(networkClient)
        callApi.getCallConfig(limit = null)
    }

    @Test
    fun givenCallApi_whenGettingCallConfigWithLimit_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_CONFIG.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertQueryExist(QUERY_KEY_LIMIT)
                assertQueryParameter(QUERY_KEY_LIMIT, hasValue = "7")
                assertPathEqual("$PATH_CALLS/$PATH_CONFIG")
            }
        )

        val callApi: CallApi = CallApiV0(networkClient)
        callApi.getCallConfig(limit = 7)
    }

    @Test
    fun givenCallApi_whenConnectingToSFT_theREquestShouldBeConfiguredCorrectly() = runTest {
        val sftConnectionURL = "sft.connection.url1"
        val sftConnectionData = """
            |{
            |   "sft":"connection"
            |}
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_SFT.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual(sftConnectionURL)
                assertJsonBodyContent(sftConnectionData)
            }
        )

        val callApi: CallApi = CallApiV0(networkClient)
        callApi.connectToSFT(
            url = sftConnectionURL,
            data = sftConnectionData
        )
    }

    private companion object {

        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"

        const val QUERY_KEY_LIMIT = "limit"

        val jsonProvider = { _: String ->
            """
            |{
            |   "call": "dummy_config"
            |}
            """.trimIndent()
        }
        val GET_CALL_CONFIG = AnyResponseProvider(data = "", jsonProvider)
        val GET_CALL_SFT = AnyResponseProvider(data = "", jsonProvider)
    }
}
