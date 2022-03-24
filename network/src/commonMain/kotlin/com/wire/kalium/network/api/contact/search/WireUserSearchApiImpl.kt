package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class WireUserSearchApiImpl(private val httpClient: HttpClient) : WireUserSearchApi {

    override suspend fun search(wireUserSearchRequest: WireUserSearchRequest): NetworkResponse<WireUserSearchResponse> =
        wrapKaliumResponse {
            httpClient.get("/$PATH_CONTACT_SEARCH") {
                with(wireUserSearchRequest) {
                    parameter(QUERY_KEY_SEARCH_QUERY, searchQuery)

                    parameter(QUERY_KEY_DOMAIN, domain)
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
