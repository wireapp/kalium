/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.api.common

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.SFTApi
import com.wire.kalium.network.api.vcommon.SFTApiImpl
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SFTApiTest : ApiTest() {

    @Test
    fun givenBareSftHost_whenConnecting_thenUseItAsTheHttpsHost() = runTest {
        assertSftUrlIsSentAs(
            sftConnectionURL = "sft.connection.url1",
            expectedUrl = "https://sft.connection.url1"
        )
    }

    @Test
    fun givenSftUrlWithHttpProtocol_whenConnecting_thenReplaceItWithHttps() = runTest {
        assertSftUrlIsSentAs(
            sftConnectionURL = "http://sft.connection.url1/test/endpoint?param1=value1",
            expectedUrl = "https://sft.connection.url1/test/endpoint?param1=value1"
        )
    }

    @Test
    fun givenSftUrlWithNotProtocol_whenConnecting_thenAddHttps() = runTest {
        assertSftUrlIsSentAs(
            sftConnectionURL = "sft.connection.url1/test/endpoint?param1=value1",
            expectedUrl = "https://sft.connection.url1/test/endpoint?param1=value1"
        )
    }

    @Test
    fun givenSftUrlWithHttpsProtocol_whenConnecting_thenDoNotChangeIt() = runTest {
        assertSftUrlIsSentAs(
            sftConnectionURL = "https://sft.connection.url1/test/endpoint?param1=value1",
            expectedUrl = "https://sft.connection.url1/test/endpoint?param1=value1"
        )
    }

    @Test
    fun givenSftAbsoluteUrl_whenConnecting_thenTheRequestGoesToThatHostWithoutCredentials() = runTest {
        val networkClient = mockUnboundNetworkClient(
            responseBody = SFT_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHostEqual("sft.example.test")
                assertNull(headers[HttpHeaders.Authorization])
            }
        )

        assertTrue(SFTApiImpl(networkClient).connectToSFT(url = "https://sft.example.test/join", data = "{}").isSuccessful())
    }

    private suspend fun assertSftUrlIsSentAs(sftConnectionURL: String, expectedUrl: String) {
        val networkClient = mockUnboundNetworkClient(
            responseBody = SFT_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertUrlEqual(expectedUrl)
                assertJsonBodyContent(SFT_CONNECTION_DATA)
            }
        )

        val sftApi: SFTApi = SFTApiImpl(networkClient)
        assertTrue(sftApi.connectToSFT(url = sftConnectionURL, data = SFT_CONNECTION_DATA).isSuccessful())
    }

    private companion object {
        const val SFT_RESPONSE = """{"call": "dummy_config"}"""
        val SFT_CONNECTION_DATA = """
            |{
            |   "sft":"connection"
            |}
        """.trimMargin()
    }
}
