package com.wire.kalium.api

import com.wire.kalium.api.tools.testCredentials
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer
import com.wire.kalium.network.tools.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            sessionCredentials = testCredentials,
            backEndConfig = TEST_BACKEND_CONFIG
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
        assertion: (HttpRequestData.() -> Unit) = {}
    ): HttpClient = mockAuthenticatedHttpClient(ByteReadChannel(responseBody), statusCode, assertion)

    private fun mockUnauthenticatedHttpClient(
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
        return LoginNetworkContainer(
            engine = mockEngine,
            isRequestLoggingEnabled = true,
            backEndConfig = TEST_BACKEND_CONFIG
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
            sessionCredentials = testCredentials,
            backEndConfig = TEST_BACKEND_CONFIG
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
    fun HttpRequestData.assertPathEqual(path: String) = assertEquals(this.url.encodedPath, path)


    private companion object {
        val TEST_BACKEND_CONFIG = BackendConfig("", "", "")
    }
}
