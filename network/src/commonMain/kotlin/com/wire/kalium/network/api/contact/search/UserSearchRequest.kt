package com.wire.kalium.network.api.contact.search

data class UserSearchRequest(
    val searchQuery: String,
    val domain: String,
    val maxResultSize: Int? = null
)
