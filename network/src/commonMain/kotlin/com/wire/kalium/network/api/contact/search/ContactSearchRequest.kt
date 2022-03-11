package com.wire.kalium.network.api.contact.search

data class ContactSearchRequest(
    val searchQuery: String,
    val domain: String,
    val resultSize: Int? = null
)
