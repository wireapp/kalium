package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ContactSearchApiImpl(private val httpClient: HttpClient) : ContactSearchApi {

    override suspend fun search(contactSearchRequest: ContactSearchRequest): NetworkResponse<ContactSearchResponse> =
        wrapKaliumResponse {
            httpClient.get("/$PATH_CONTACT_SEARCH") {
                with(contactSearchRequest) {
                    parameter(QUERY_KEY_SEARCH_QUERY, searchQuery)

                    domain?.let { parameter(QUERY_KEY_DOMAIN, it) }
                    resultSize?.let { parameter(QUERY_KEY_SIZE, it) }
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
