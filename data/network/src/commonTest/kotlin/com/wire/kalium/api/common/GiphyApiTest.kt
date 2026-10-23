/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.api.common

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifsResponseDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphySearchSuggestionsResponseDTO
import com.wire.kalium.network.api.vcommon.GiphyApiImpl
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNotFound
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GiphyApiTest : ApiTest() {

    @Test
    fun givenQuery_whenSearching_thenCallsTheProxyWithRatingAndLanguage() = runTest {
        val api = giphyApi(
            responseBody = SEARCH_RESPONSE,
            assertion = {
                assertGet()
                assertPathEqual("/proxy/giphy/v1/gifs/search")
                assertQueryParameter("q", hasValue = "happy friday")
                assertQueryParameter("limit", hasValue = "24")
                assertQueryParameter("offset", hasValue = "48")
                assertQueryParameter("rating", hasValue = "pg-13")
                assertQueryParameter("lang", hasValue = "de")
                assertAuthorizationHeaderExist()
            }
        )

        val result = api.search(query = "happy friday", limit = 24, offset = 48, rating = "pg-13", language = "de")

        assertIs<NetworkResponse.Success<GiphyGifsResponseDTO>>(result)
        val gif = result.value.data.single()
        assertEquals("abc123", gif.id)
        assertEquals("Happy Friday Dance GIF by Studio", gif.title)
        assertEquals("https://media2.giphy.com/media/abc123/giphy.gif", gif.images.downsized?.url)
        assertEquals("480", gif.images.downsized?.width)
        assertEquals("1234567", gif.images.downsized?.size)
        assertEquals("200", gif.images.fixedWidthDownsampled?.width)
        assertEquals(500, result.value.pagination?.totalCount)
        assertEquals(1, result.value.pagination?.count)
        assertEquals(48, result.value.pagination?.offset)
    }

    @Test
    fun givenNoLanguage_whenSearching_thenOmitsTheLanguage() = runTest {
        val api = giphyApi(responseBody = SEARCH_RESPONSE, assertion = { assertQueryDoesNotExist("lang") })

        assertIs<NetworkResponse.Success<*>>(api.search(query = "cat", limit = 24, offset = 0, rating = "pg-13", language = null))
    }

    @Test
    fun givenTrendingRequest_whenFetching_thenCallsTheTrendingProxyPath() = runTest {
        val api = giphyApi(
            responseBody = SEARCH_RESPONSE,
            assertion = {
                assertGet()
                assertPathEqual("/proxy/giphy/v1/gifs/trending")
                assertQueryDoesNotExist("q")
                assertQueryParameter("rating", hasValue = "pg-13")
                assertQueryParameter("offset", hasValue = "0")
            }
        )

        assertIs<NetworkResponse.Success<*>>(api.trending(limit = 24, offset = 0, rating = "pg-13"))
    }

    @Test
    fun givenQuery_whenFetchingSuggestions_thenCallsSearchTags() = runTest {
        val api = giphyApi(
            responseBody = """{"data":[{"name":"happy friday dance"},{"name":"happy friday office"}]}""",
            assertion = {
                assertPathEqual("/proxy/giphy/v1/gifs/search/tags")
                assertQueryParameter("q", hasValue = "happy fri")
            }
        )

        val result = api.searchSuggestions("happy fri")

        assertIs<NetworkResponse.Success<GiphySearchSuggestionsResponseDTO>>(result)
        assertEquals(listOf("happy friday dance", "happy friday office"), result.value.data.map { it.name })
    }

    @Test
    fun givenBackendWithoutTheProxy_whenSearching_thenReturnsNotFound() = runTest {
        val api = giphyApi(responseBody = "<html>Not Found</html>", statusCode = HttpStatusCode.NotFound)

        val result = api.search(query = "cat", limit = 24, offset = 0, rating = "pg-13", language = null)

        assertIs<NetworkResponse.Error>(result)
        val exception = assertIs<KaliumException.InvalidRequestError>(result.kException)
        assertTrue(exception.isNotFound())
    }

    @Test
    fun givenGifUrl_whenDownloading_thenWritesTheBytesWithoutWireCredentials() = runTest {
        val api = GiphyApiImpl(
            authenticatedNetworkClient = mockAuthenticatedNetworkClient("", HttpStatusCode.OK),
            mediaNetworkClient = mockUnboundNetworkClient(
                responseBody = GIF_BODY,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertHostEqual("media2.giphy.com")
                    assertNull(headers[HttpHeaders.Authorization])
                },
                headers = mapOf(HttpHeaders.ContentType to "image/gif"),
            ),
        )
        val sink = Buffer()

        val result = api.downloadGif("https://media2.giphy.com/media/abc123/giphy.gif", maxSizeInBytes = 1024, sink = sink)

        assertIs<NetworkResponse.Success<Long>>(result)
        assertEquals(GIF_BODY.length.toLong(), result.value)
        assertEquals(GIF_BODY, sink.readUtf8())
    }

    @Test
    fun givenGifLargerThanTheLimit_whenDownloading_thenFails() = runTest {
        val api = GiphyApiImpl(
            authenticatedNetworkClient = mockAuthenticatedNetworkClient("", HttpStatusCode.OK),
            mediaNetworkClient = mockUnboundNetworkClient(
                responseBody = GIF_BODY,
                statusCode = HttpStatusCode.OK,
                headers = mapOf(HttpHeaders.ContentType to "image/gif"),
            ),
        )

        val result = api.downloadGif("https://media2.giphy.com/media/abc123/giphy.gif", maxSizeInBytes = 4, sink = Buffer())

        assertIs<NetworkResponse.Error>(result)
    }

    private fun giphyApi(
        responseBody: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        assertion: suspend HttpRequestData.() -> Unit = {},
    ) = GiphyApiImpl(
        authenticatedNetworkClient = mockAuthenticatedNetworkClient(responseBody, statusCode, assertion),
        mediaNetworkClient = mockUnboundNetworkClient("", HttpStatusCode.OK),
    )

    private companion object {
        const val GIF_BODY = "GIF89a-fake-gif-bytes"
        val SEARCH_RESPONSE = """
            {
              "data": [
                {
                  "type": "gif",
                  "id": "abc123",
                  "title": "Happy Friday Dance GIF by Studio",
                  "analytics": {"onsent": {"url": "https://giphy-analytics.giphy.com/"}},
                  "images": {
                    "downsized": {"url": "https://media2.giphy.com/media/abc123/giphy.gif", "width": "480", "height": "270", "size": "1234567"},
                    "fixed_width_downsampled": {"url": "https://media2.giphy.com/media/abc123/200w_d.gif", "width": "200", "height": "113", "size": "98765"},
                    "looping": {"mp4": "https://media2.giphy.com/media/abc123/giphy-loop.mp4"}
                  }
                }
              ],
              "pagination": {"total_count": 500, "count": 1, "offset": 48},
              "meta": {"status": 200, "msg": "OK", "response_id": "r1"}
            }
        """.trimIndent()
    }
}
