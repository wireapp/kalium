package com.wire.kalium.logic.configuration

sealed class BuildType {
    object Release : BuildType()
    object Debug : BuildType()
    data class Custom(val networkConfig: NetworkConfig) : BuildType()
}

data class NetworkConfig(
    val apiBaseUrl: String,
    val accountsUrl: String,
    val webSocketUrl: String
)
