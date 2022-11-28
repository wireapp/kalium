package com.wire.kalium.api.v0.search

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchRequest
import com.wire.kalium.network.api.v0.authenticated.UserSearchApiV0
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UserSearchApiV0Test : ApiTest() {

    @Test
    fun givenRequestWithSearchQueryAndDomain_whenCallingSearchContact_ThenRequestShouldReturnExpectedAssertion() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
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

            val userSearchApi: UserSearchApi = UserSearchApiV0(networkClient)
            userSearchApi.search(
                UserSearchRequest(
                    searchQuery = DUMMY_SEARCH_QUERY,
                    domain = DUMMY_DOMAIN,
                )
            )
        }

    @Test
    fun givenRequestWithSearchQueryAndDomainAndResultSize_whenCallingSearchContact_ThenRequestShouldReturnExpectedAssertion() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
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

            val userSearchApi: UserSearchApi = UserSearchApiV0(networkClient)
            userSearchApi.search(
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
