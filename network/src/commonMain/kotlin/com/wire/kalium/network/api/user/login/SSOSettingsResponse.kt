package com.wire.kalium.network.api.user.login


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class SSOSettingsResponse(
    @SerialName("default_sso_code")
    val defaultCode: String
)
