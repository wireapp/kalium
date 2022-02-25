package com.wire.kalium.network.api.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ServerConfigResponse(
    @SerialName("endpoints") val endpoints: EndPoints,
    @SerialName("title") val title: String
)

@Serializable
data class EndPoints(
    @SerialName("backendURL") val apiBaseUrl: String,
    @SerialName("backendWSURL") val webSocketBaseUrl: String,
    @SerialName("blackListURL") val blackListUrl: String,
    @SerialName("teamsURL") val teamsUrl: String,
    @SerialName("accountsURL") val accountsBaseUrl: String,
    @SerialName("websiteURL") val websiteUrl: String
)
