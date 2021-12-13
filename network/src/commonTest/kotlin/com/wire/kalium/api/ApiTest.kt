package com.wire.kalium.api

import com.wire.kalium.network.NetworkModule
import com.wire.kalium.network.api.CredentialsProvider
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class TestAuthManager() : CredentialsProvider {
    override fun accessToken(): String =
        "eyJhbGciOiJIUzI1AnwarInR5cCI6IkpXVCJ9.eyJsb2dnZWRJbkFzIjoiYWRtaW4iLCJpYXQiOjE0MjI3Nzk2Mz69.gzSraSYS8EXBxLN_oWnFSRgCzcmJmMjLiuyu5CSpyHI"

    override fun refreshToken(): String =
        "a123bGciOiJIUzI1NiIsInR5cCI6IkpX2fr9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6Ik420G4gRG9lIiwiYWRtaW4iOnRydWV9.TJVA95OrM7E2cBab30RMHrHDcEfxjoYZgeFONFh7HgQ"
}

interface ApiTest {

    /**
     * creates a mock Ktor Http client
     * @param responseBody the response body as Json string
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockHttpClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {}
    ): HttpClient = mockHttpClient(ByteReadChannel(responseBody), statusCode, assertion)

    /**
     * creates a mock Ktor Http client
     * @param responseBody the response body as ByteReadChannel
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockHttpClient(
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
        return NetworkModule(
            engine = mockEngine,
            credentialsProvider = TestAuthManager()
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
        assertEquals(this.body.contentType, ContentType.Application.Json)

    // path
    fun HttpRequestData.assertPathEqual(path: String) = assertEquals(this.url.encodedPath, path)
}
