package com.wire.kalium.network.api.contact.search

import kotlinx.serialization.SerialName

data class ContactSearchRequest(
    @SerialName("q")
    val searchQuery: String,
    @SerialName("domain")
    val domain: String? = null,
    @SerialName("size")
    val resultSize: Int? = null
)
