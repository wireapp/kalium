package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class UserSearchApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : UserSearchApi  {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun search(userSearchRequest: UserSearchRequest): NetworkResponse<UserSearchResponse> =
        wrapKaliumResponse {
            httpClient.get("/$PATH_CONTACT_SEARCH") {
                with(userSearchRequest) {
                    parameter(QUERY_KEY_SEARCH_QUERY, searchQuery)
                    if(domain.isNotBlank()) {
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
