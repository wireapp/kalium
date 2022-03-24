package com.wire.kalium.network.api.contact.search

import com.wire.kalium.network.utils.NetworkResponse

interface WireUserSearchApi {

    suspend fun search(
        wireUserSearchRequest: WireUserSearchRequest
    ): NetworkResponse<WireUserSearchResponse>

}
