package com.wire.kalium.api.tools.json.api.contact.search

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.contact.search.WireUserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchApiImpl
import com.wire.kalium.network.api.contact.search.UserSearchRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ContactDTOSearchApiTest : ApiTest {

    @Test
    fun givenRequestWithSearchQueryAndDomain_whenCallingSearchContact_ThenRequestShouldReturnExpectedAssertion() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                responseBody = "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist(QUERY_KEY_SEARCH_QUERY)
                    assertQueryExist(QUERY_KEY_DOMAIN)
                    assertQueryDoesNotExist(QUERY_KEY_SIZE)
                    assertQueryParameter(QUERY_KEY_SEARCH_QUERY, hasValue = DUMMY_SEARCH_QUERY)
                    assertQueryParameter(QUERY_KEY_DOMAIN, hasValue = DUMMY_DOMAIN)
                }
            )

            val wireUserSearchApi: WireUserSearchApi = UserSearchApiImpl(httpClient)
            wireUserSearchApi.search(
                UserSearchRequest(
                    searchQuery = DUMMY_SEARCH_QUERY,
                    domain = DUMMY_DOMAIN,
                )
            )
        }

    @Test
    fun givenRequestWithSearchQueryAndDomainAndResultSize_whenCallingSearchContact_ThenRequestShouldReturnExpectedAssertion() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                responseBody = "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist(QUERY_KEY_SEARCH_QUERY)
                    assertQueryExist(QUERY_KEY_DOMAIN)
                    assertQueryExist(QUERY_KEY_SIZE)
                    assertQueryParameter(QUERY_KEY_SEARCH_QUERY, hasValue = DUMMY_SEARCH_QUERY)
                    assertQueryParameter(QUERY_KEY_DOMAIN, hasValue = DUMMY_DOMAIN)
                    assertQueryParameter(QUERY_KEY_SIZE, hasValue = DUMMY_SIZE.toString())
                }
            )

            val wireUserSearchApi: WireUserSearchApi = UserSearchApiImpl(httpClient)
            wireUserSearchApi.search(
                UserSearchRequest(
                    searchQuery = DUMMY_SEARCH_QUERY,
                    domain = DUMMY_DOMAIN,
                    maxResultSize = DUMMY_SIZE
                )
            )
        }

    private companion object {
        const val DUMMY_SEARCH_QUERY = "dummy search query"
        const val DUMMY_DOMAIN = "dummy domain"
        const val DUMMY_SIZE = 100

        const val QUERY_KEY_SEARCH_QUERY = "q"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_DOMAIN = "domain"
    }
}
