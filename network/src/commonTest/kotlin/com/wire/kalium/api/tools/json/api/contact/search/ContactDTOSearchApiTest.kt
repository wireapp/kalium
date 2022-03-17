package com.wire.kalium.api.tools.json.api.contact.search

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.contact.search.ContactSearchApiImpl
import com.wire.kalium.network.api.contact.search.ContactSearchRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ContactDTOSearchApiTest : ApiTest {

    @Test
    fun givenRequestWithSearchQuery_whenCallingSearchContact_ThenRequestShouldReturnExpectedAssertion() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                responseBody = "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist(QUERY_KEY_SEARCH_QUERY)
                    assertQueryDoesNotExist(QUERY_KEY_SIZE)
                    assertQueryDoesNotExist(QUERY_KEY_DOMAIN)
                    assertQueryParameter(QUERY_KEY_SEARCH_QUERY, hasValue = DUMMY_SEARCH_QUERY)
                }
            )

            val contactSearchApi: ContactSearchApi = ContactSearchApiImpl(httpClient)
            contactSearchApi.search(
                ContactSearchRequest(
                    searchQuery = DUMMY_SEARCH_QUERY,
                    domain = DUMMY_DOMAIN
                )
            )
        }

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

            val contactSearchApi: ContactSearchApi = ContactSearchApiImpl(httpClient)
            contactSearchApi.search(
                ContactSearchRequest(
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

            val contactSearchApi: ContactSearchApi = ContactSearchApiImpl(httpClient)
            contactSearchApi.search(
                ContactSearchRequest(
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
