package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConversationAccess {
    @SerialName("private")
    Private,
    @SerialName("code")
    Code,
    @SerialName("invite")
    Invite,
    @SerialName("link")
    Link;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
