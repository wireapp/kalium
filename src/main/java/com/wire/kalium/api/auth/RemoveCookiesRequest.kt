package com.wire.kalium.api.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class RemoveCookiesRequest

@Serializable
data class RemoveCookiesWithIds(
        @SerialName("ids") val cookiesId: List<String>,
        @SerialName("password") val password: String
): RemoveCookiesRequest()

@Serializable
data class RemoveCookiesWithLabels(
        @SerialName("labels") val labels: List<String>,
        @SerialName("password") val password: String
): RemoveCookiesRequest()
