package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse

interface ContactSearchApi {

    suspend fun search(
        contactSearchRequest: ContactSearchRequest
    ): NetworkResponse<ContactSearchResponse>

}
