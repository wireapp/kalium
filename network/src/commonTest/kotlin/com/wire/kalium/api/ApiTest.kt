package com.wire.kalium.api

import com.wire.kalium.api.tools.testCredentials
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.UnauthenticatedNetworkContainer
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
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
import kotlin.test.fail

class TestSessionManager : SessionManager {
    private val serverConfig = TEST_BACKEND_CONFIG
    private var session = testCredentials

    override fun session(): Pair<SessionDTO, ServerConfigDTO> = Pair(session, serverConfig)

    override fun updateSession(newAccessTokenDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        SessionDTO(
            session.userId,
            newAccessTokenDTO.tokenType,
            newAccessTokenDTO.value,
            newRefreshTokenDTO?.value ?: session.refreshToken
        )

    override fun onSessionExpired() {
        TODO("Not yet implemented")
    }

    companion object {
        val SESSION = testCredentials
    }

}

internal interface ApiTest {

    val TEST_SESSION_NAMAGER: TestSessionManager get() = TestSessionManager()

    /**
     * creates an authenticated mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockAuthenticatedNetworkClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): AuthenticatedNetworkClient = mockAuthenticatedNetworkClient(ByteReadChannel(responseBody), statusCode, assertion)

    private fun mockAuthenticatedNetworkClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): AuthenticatedNetworkClient {
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
            sessionManager = TEST_SESSION_NAMAGER
        ).networkClient
    }

    fun mockWebsocketClient(): AuthenticatedWebSocketClient {
        val mockEngine = MockEngine {
            TODO("It's not yet possible to mock WebSockets from the client side")
        }
        return AuthenticatedNetworkContainer(
            engine = mockEngine,
            sessionManager = TEST_SESSION_NAMAGER
        ).websocketClient
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
        headers: Map<String, String>? = null
    ): UnauthenticatedNetworkClient = mockUnauthenticatedNetworkClient(ByteReadChannel(responseBody), statusCode, assertion, headers)

    private fun mockUnauthenticatedNetworkClient(
        responseBody: ByteReadChannel,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>?
    ): UnauthenticatedNetworkClient {
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
        return UnauthenticatedNetworkContainer(
            backendLinks = TEST_BACKEND_Links,
            engine = mockEngine
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
        expectedRequests: List<TestRequestHandler>
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
        return UnauthenticatedNetworkContainer(
            backendLinks = TEST_BACKEND_Links,
            engine = mockEngine
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
        assertion: (HttpRequestData.() -> Unit) = {}
    ): AuthenticatedNetworkClient {
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
            sessionManager = TEST_SESSION_NAMAGER
        ).networkClient
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

    // content type
    fun HttpRequestData.assertJson() = assertContentType(ContentType.Application.Json.withParameter("charset", "UTF-8"))
    fun HttpRequestData.assertContentType(contentType: ContentType) =
        assertTrue(
            contentType.match(this.body.contentType ?: ContentType.Any),
            "contentType: ${this.body.contentType} doesn't match expected contentType: $contentType"
        )

    // path
    // assertContains is used here instead of equals because the path can contain other data like api version
    fun HttpRequestData.assertPathEqual(path: String) = assertContains(this.url.encodedPath, path)

    // path and query
    fun HttpRequestData.assertPathAndQueryEqual(pathAndQuery: String) = assertEquals(pathAndQuery, this.url.encodedPathAndQuery)

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
}
