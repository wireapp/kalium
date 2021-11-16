package com.wire.kalium.api.auth

import kotlinx.serialization.SerialName

sealed class RemoveCookiesRequest

data class RemoveCookiesWithIds(
        @SerialName("ids") val cookiesId: List<String>,
        @SerialName("password") val password: String
): RemoveCookiesRequest()

data class RemoveCookiesWithLabels(
        @SerialName("labels") val labels: List<String>,
        @SerialName("password") val password: String
): RemoveCookiesRequest()
