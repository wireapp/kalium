package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConversationAccessDTO {
    @SerialName("private")
    PRIVATE,
    @SerialName("code")
    CODE,
    @SerialName("invite")
    INVITE,
    @SerialName("link")
    LINK;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
