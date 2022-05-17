package com.wire.kalium.network.tools

import io.ktor.http.Url

data class ServerConfigDTO(
    val apiBaseUrl: Url,
    val accountsBaseUrl: Url,
    val webSocketBaseUrl: Url,
    val blackListUrl: Url,
    val teamsUrl: Url,
    val websiteUrl: Url,
    val title: String,
    val apiVersion: Int
)
