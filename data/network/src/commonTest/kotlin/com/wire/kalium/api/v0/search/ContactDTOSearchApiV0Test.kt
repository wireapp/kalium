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

package com.wire.kalium.api.v0.search

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.authenticated.search.UserSearchRequest
import com.wire.kalium.network.api.v0.authenticated.UserSearchApiV0
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class ContactDTOSearchApiV0Test : ApiTest() {

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

            val wireUserSearchApi: UserSearchApi = UserSearchApiV0(networkClient)
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
