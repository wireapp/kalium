package com.wire.kalium.network.tools

data class BackendConfig(
    val apiBaseUrl: String,
    val accountsBaseUrl: String,
    val webSocketBaseUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String,
    val title: String
)
