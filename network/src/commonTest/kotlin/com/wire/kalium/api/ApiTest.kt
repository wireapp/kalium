package com.wire.kalium.api

import com.wire.kalium.api.tools.testCredentials
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.tools.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

interface ApiTest {
    /**
     * creates an authenticated mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockAuthenticatedHttpClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): HttpClient = mockAuthenticatedHttpClient(ByteReadChannel(responseBody), statusCode, assertion)

    private fun mockAuthenticatedHttpClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            request.assertion()
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return AuthenticatedNetworkContainer(
            engine = mockEngine,
            sessionDTO = testCredentials,
            backendConfig = TEST_BACKEND_CONFIG,
            kaliumLogger = TEST_LOGGER_CONFIG
        ).authenticatedHttpClient
    }

    /**
     * creates an unauthenticated mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockUnauthenticatedHttpClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null
    ): HttpClient = mockUnauthenticatedHttpClient(ByteReadChannel(responseBody), statusCode, assertion, headers)

    private fun mockUnauthenticatedHttpClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>?
    ): HttpClient {
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
        return LoginNetworkContainer(
            engine = mockEngine,
            kaliumLogger = TEST_LOGGER_CONFIG
        ).anonymousHttpClient
    }

    /**
     * Creates a mock Ktor Http client
     * @param responseBody the response body as a ByteArray
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockAuthenticatedHttpClient(
        responseBody: ByteArray,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            request.assertion()
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return AuthenticatedNetworkContainer(
            engine = mockEngine,
            sessionDTO = testCredentials,
            backendConfig = TEST_BACKEND_CONFIG,
            kaliumLogger = TEST_LOGGER_CONFIG
        ).authenticatedHttpClient
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

    // content type
    fun HttpRequestData.assertJson() = assertContentType(ContentType.Application.Json)
    private fun HttpRequestData.assertContentType(contentType: ContentType) =
        assertContains(this.body.contentType?.contentType ?: "", contentType.contentType)

    // path
    fun HttpRequestData.assertPathEqual(path: String) = assertEquals(path, this.url.encodedPath)

    // body
    fun HttpRequestData.assertBodyContent(content: String) {
        assertIs<TextContent>(body)
        // convert both body and the content to JsonObject, so we are not comparing strings
        // since json strings can have different values order
        val expected = buildJsonObject { buildString { content } }
        val actual = buildJsonObject { buildString { (body as TextContent).text } }
        assertEquals(expected, actual)
    }

    // host
    fun HttpRequestData.assertHostEqual(expectedHost: String) = assertEquals(expected = expectedHost, actual = this.url.host)
    fun HttpRequestData.assertHttps() = assertEquals(expected = URLProtocol.HTTPS, actual = this.url.protocol)

    private companion object {
        val TEST_BACKEND_CONFIG =
            BackendConfig(
                "test.api.com", "test.account.com", "test.ws.com",
                "test.blacklist", "test.teams.com", "test.wire.com", "Test Title"
            )

        val  TEST_LOGGER_CONFIG =
            KaliumLogger(config = KaliumLogger.Config.DISABLED)
    }
}
