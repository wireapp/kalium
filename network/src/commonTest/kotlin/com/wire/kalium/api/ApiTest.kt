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

package com.wire.kalium.api

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.api.v0.authenticated.networkContainer.AuthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v0.unauthenticated.networkContainer.UnauthenticatedNetworkContainerV0
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.serialization.JoseJson
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

internal abstract class ApiTest {

    init {
        KaliumUserAgentProvider.setUserAgent("test/useragent")
    }

    private val json get() = KtxSerializer.json
    val TEST_SESSION_MANAGER: TestSessionManagerV0 get() = TestSessionManagerV0()

    private val loadToken: suspend () -> BearerTokens?
        get() = {
            val session = TEST_SESSION_MANAGER.session()
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }

    private val refreshToken: suspend RefreshTokensParams.() -> BearerTokens?
        get() = {
            val newSession = TEST_SESSION_MANAGER.updateToken(
                accessTokenApi = AccessTokenApiV0(client),
                oldRefreshToken = oldTokens!!.refreshToken
            )
            newSession.let {
                BearerTokens(accessToken = it.accessToken, refreshToken = it.refreshToken)
            }
        }

    val TEST_BEARER_AUTH_PROVIDER get() = BearerAuthProvider(refreshToken, loadToken, { true }, null)

    /**
     * creates an authenticated mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    protected fun mockAuthenticatedNetworkClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: suspend (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String> = mutableMapOf(),
    ): AuthenticatedNetworkClient =
        mockAuthenticatedNetworkClient(ByteReadChannel(responseBody), statusCode, assertion, headers)

    fun mockAuthenticatedNetworkClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: suspend (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>?,
    ): AuthenticatedNetworkClient {
        val head: Map<String, List<String>> = (headers?.let {
            mutableMapOf(HttpHeaders.ContentType to "application/json").plus(headers).mapValues { listOf(it.value) }
        } ?: run {
            mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
        })
        val mockEngine = MockEngine { request ->
            request.assertion()
            respond(
                content = responseBody,
                status = statusCode,
                headers = HeadersImpl(head)
            )
        }
        return AuthenticatedNetworkContainerV0(
            engine = mockEngine,
            sessionManager = TEST_SESSION_MANAGER,
            certificatePinning = emptyMap(),
            mockEngine = null,
            kaliumLogger = kaliumLogger,
            mockWebSocketSession = null
        ).networkClient
    }

    fun mockWebsocketClient(): AuthenticatedWebSocketClient {
        val mockEngine = MockEngine {
            TODO("It's not yet possible to mock WebSockets from the client side")
        }
        return AuthenticatedNetworkContainerV0(
            engine = mockEngine,
            sessionManager = TEST_SESSION_MANAGER,
            certificatePinning = emptyMap(),
            mockEngine = null,
            kaliumLogger = kaliumLogger,
            mockWebSocketSession = null
        ).websocketClient
    }

    fun mockAssetsHttpClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null,
    ): AuthenticatedNetworkClient {
        val mockEngine = createMockEngine(responseBody, statusCode, assertion, headers)
        return AuthenticatedNetworkClient(
            engine = mockEngine,
            serverConfigDTO = TEST_SESSION_MANAGER.serverConfig(),
            bearerAuthProvider = TEST_BEARER_AUTH_PROVIDER,
            kaliumLogger = kaliumLogger
        )
    }

    /**
     * creates an unauthenticated mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockUnauthenticatedNetworkClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null,
        developmentApiEnabled: Boolean = false,
    ): UnauthenticatedNetworkClient =
        mockUnauthenticatedNetworkClient(
            ByteReadChannel(responseBody),
            statusCode,
            assertion,
            headers,
            developmentApiEnabled,
        )

    private fun mockUnauthenticatedNetworkClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>?,
        developmentApiEnabled: Boolean = false,
    ): UnauthenticatedNetworkClient {

        val mockEngine = createMockEngine(responseBody, statusCode, assertion, headers)

        return UnauthenticatedNetworkContainerV0(
            backendLinks = TEST_BACKEND,
            engine = mockEngine,
            proxyCredentials = null,
            certificatePinning = emptyMap(),
            mockEngine = null,
            developmentApiEnabled = developmentApiEnabled
        ).unauthenticatedNetworkClient
    }

    class TestRequestHandler(
        val path: String,
        val responseBody: String,
        val statusCode: HttpStatusCode,
        val assertion: (HttpRequestData.() -> Unit) = {},
        val headers: Map<String, String>? = null
    )

    fun mockUnauthenticatedNetworkClient(
        expectedRequests: List<TestRequestHandler>,
        developmentApiEnabled: Boolean = false,
    ): UnauthenticatedNetworkClient {
        val mockEngine = MockEngine { currentRequest ->
            expectedRequests.forEach { request ->
                val head: Map<String, List<String>> = (request.headers?.let {
                    mutableMapOf(HttpHeaders.ContentType to "application/json").plus(request.headers).mapValues { listOf(it.value) }
                } ?: run {
                    mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
                })
                if (request.path == currentRequest.url.encodedPath) {
                    return@MockEngine respond(
                        content = ByteReadChannel(request.responseBody),
                        status = request.statusCode,
                        headers = HeadersImpl(head)
                    )
                }
            }
            fail("no expected response was found for ${currentRequest.method.value}:${currentRequest.url}")
        }
        return UnauthenticatedNetworkContainerV0(
            backendLinks = TEST_BACKEND,
            engine = mockEngine,
            proxyCredentials = null,
            developmentApiEnabled = developmentApiEnabled,
            certificatePinning = emptyMap(),
            mockEngine = null
        ).unauthenticatedNetworkClient
    }

    /**
     * Creates a mock Ktor Http client
     * @param responseBody the response body as a ByteArray
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockAuthenticatedNetworkClient(
        responseBody: ByteArray,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null,
        ): AuthenticatedNetworkClient {
        val mockEngine = createMockEngine(
            ByteReadChannel(responseBody),
            statusCode,
            assertion,
            headers
        )
        return AuthenticatedNetworkContainerV0(
            engine = mockEngine,
            sessionManager = TEST_SESSION_MANAGER,
            certificatePinning = emptyMap(),
            mockEngine = null,
            kaliumLogger = kaliumLogger,
            mockWebSocketSession = null
        ).networkClient
    }

    /**
     * Creates a mock Ktor Http client
     * @param responseBody the response body as a ByteArray
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockUnboundNetworkClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null,
    ): UnboundNetworkClient {
        val mockEngine = createMockEngine(
            ByteReadChannel(responseBody),
            statusCode,
            assertion,
            headers
        )
        return UnboundNetworkClient(
            engine = mockEngine,
        )
    }

    private fun createMockEngine(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null
    ): MockEngine {
        val newHeaders: Map<String, List<String>> = (headers?.let {
            headers.mapValues { listOf(it.value) }
        } ?: run {
            mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
        })

        return MockEngine { request ->
            request.assertion()
            respond(
                content = responseBody,
                status = statusCode,
                headers = HeadersImpl(newHeaders)
            )
        }
    }

    // query params assertions
    /**
     * assert the request url has query parameter value
     * @param name name of the query parameter
     */
    fun HttpRequestData.assertQueryExist(name: String) = assertFalse(this.url.parameters[name].isNullOrBlank())

    /**
     * assert the request url does not have query parameter value
     * @param name name of the query parameter
     */
    fun HttpRequestData.assertQueryDoesNotExist(name: String) = assertTrue(this.url.parameters[name].isNullOrBlank())

    /**
     * assert the request url has query parameter value
     * @param name name of the query parameter
     * @param hasValue the value of the parameter
     */
    fun HttpRequestData.assertQueryParameter(name: String, hasValue: String) = assertEquals(this.url.parameters[name], hasValue)

    /**
     * assert the request url has no query params
     */
    fun HttpRequestData.assertNoQueryParams() = assertTrue(this.url.parameters.names().isEmpty())

    // request headers assertions
    fun HttpRequestData.assertHeaderExist(name: String) = assertFalse(this.headers[name].isNullOrBlank())
    fun HttpRequestData.assertHeaderEqual(name: String, value: String) = assertEquals(value, this.headers[name])

    fun HttpRequestData.assertAuthorizationHeaderExist() = this.assertHeaderExist(HttpHeaders.Authorization)

    // http method assertion
    private fun HttpRequestData.assertMethodType(method: HttpMethod) = assertEquals(this.method, method)
    fun HttpRequestData.assertPost() = this.assertMethodType(HttpMethod.Post)
    fun HttpRequestData.assertGet() = this.assertMethodType(HttpMethod.Get)
    fun HttpRequestData.assertPut() = this.assertMethodType(HttpMethod.Put)
    fun HttpRequestData.assertDelete() = this.assertMethodType(HttpMethod.Delete)
    fun HttpRequestData.assertPatch() = this.assertMethodType(HttpMethod.Patch)
    fun HttpRequestData.assertHead() = this.assertMethodType(HttpMethod.Head)
    fun HttpRequestData.assertOptions() = this.assertMethodType(HttpMethod.Options)
    fun HttpRequestData.assertXProtobuf() = this.assertContentType(ContentType.Application.XProtoBuf)

    // content type
    fun HttpRequestData.assertJson() = assertContentType(ContentType.Application.Json.withParameter("charset", "UTF-8"))
    fun HttpRequestData.assertJsonJose() = assertContentType(ContentType.Application.JoseJson.withParameter("charset", "UTF-8"))
    fun HttpRequestData.assertContentType(contentType: ContentType) =
        assertTrue(
            contentType.match(this.body.contentType ?: ContentType.Any),
            "contentType: ${this.body.contentType} doesn't match expected contentType: $contentType"
        )

    // path
    // assertContains is used here instead of equals because the path can contain other data like api version
    fun HttpRequestData.assertPathEqual(path: String) = assertContains(this.url.encodedPath, path)

    // full url
    fun HttpRequestData.assertUrlEqual(path: String) = assertContains(this.url.toString(), path)

    // path and query
    fun HttpRequestData.assertPathAndQueryEqual(pathAndQuery: String) = assertEquals(pathAndQuery, this.url.encodedPathAndQuery)

    // body
    fun HttpRequestData.assertJsonBodyContent(content: String) {
        assertIs<TextContent>(body)
        // convert both body and the content to JsonObject, so we are not comparing strings
        // since json strings can have different values order

        val expected = json.decodeFromString<JsonElement>(content)
        val actual = json.decodeFromString<JsonElement>((body as TextContent).text)
        assertEquals(expected, actual)
    }

    // host
    fun HttpRequestData.assertHostEqual(expectedHost: String) = assertEquals(expected = expectedHost, actual = this.url.host)
    fun HttpRequestData.assertHttps() = assertEquals(expected = URLProtocol.HTTPS, actual = this.url.protocol)
}
