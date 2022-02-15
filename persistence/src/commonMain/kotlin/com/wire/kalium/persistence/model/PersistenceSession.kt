package com.wire.kalium.persistence.model

import kotlinx.serialization.Serializable

@Serializable
data class NetworkConfig(
    val apiBaseUrl: String,
    val accountBaseUrl: String,
    val webSocketBaseUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String
)

@Serializable
data class PersistenceSession(
    // TODO: replace string with UserId value/domain
    val userId: String,
    //val clientId: String,
    //val domain: String,
    val tokenType: String,
    val accessToken: String,
    val refreshToken: String,
    val networkConfig: NetworkConfig
)
