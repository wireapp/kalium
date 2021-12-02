package com.wire.kalium.network.api.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class RemoveCookiesByIdsRequest(
    @SerialName("ids") val cookiesId: List<String>,
    @SerialName("password") val password: String
)

@Serializable
data class RemoveCookiesByLabels(
    @SerialName("labels") val labels: List<String>,
    @SerialName("password") val password: String
)
