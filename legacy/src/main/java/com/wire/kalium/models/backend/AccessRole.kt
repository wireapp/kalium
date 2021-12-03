package com.wire.kalium.models.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccessRole {
    @SerialName("private")
    PRIVATE,
    @SerialName("code")
    CODE,
    @SerialName("invite")
    INVITE,
    @SerialName("link")
    LINK
}
