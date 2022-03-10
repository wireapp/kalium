package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class ContactSearchApiImpl(private val httpClient: HttpClient) : ContactSearchApi {

    override suspend fun search(contactSearchRequest: ContactSearchRequest): NetworkResponse<ContactSearchResponse> =
        with(contactSearchRequest) {
            wrapKaliumResponse {
                httpClient.get(""" /$PATH_CONTACT_SEARCH/${searchQuery + domain?.let { "/$it" } + resultSize?.let { "/$it" }} """)
            }
        }

    private companion object {
        const val PATH_CONTACT_SEARCH = "search/contacts"
    }
}
