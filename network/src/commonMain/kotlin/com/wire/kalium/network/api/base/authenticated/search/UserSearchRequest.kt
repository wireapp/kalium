package com.wire.kalium.network.api.base.authenticated.search

data class UserSearchRequest(
    val searchQuery: String,
    val domain: String,
    val maxResultSize: Int? = null
)
