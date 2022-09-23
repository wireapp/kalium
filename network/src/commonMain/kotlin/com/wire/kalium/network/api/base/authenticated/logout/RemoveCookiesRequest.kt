package com.wire.kalium.network.api.base.authenticated.logout

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
