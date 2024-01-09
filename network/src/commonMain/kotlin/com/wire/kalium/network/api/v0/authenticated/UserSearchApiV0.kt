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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchRequest
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal open class UserSearchApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : UserSearchApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun search(userSearchRequest: UserSearchRequest): NetworkResponse<UserSearchResponse> =
        wrapKaliumResponse {
            httpClient.get(PATH_CONTACT_SEARCH) {
                with(userSearchRequest) {
                    parameter(QUERY_KEY_SEARCH_QUERY, searchQuery)
                    if (domain.isNotBlank()) {
                        parameter(QUERY_KEY_DOMAIN, domain)
                    }
                    maxResultSize?.let { parameter(QUERY_KEY_SIZE, it) }
                }
            }
        }

    private companion object {
        const val PATH_CONTACT_SEARCH = "search/contacts"
        const val QUERY_KEY_SEARCH_QUERY = "q"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_DOMAIN = "domain"
    }
}
