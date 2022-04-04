package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName

enum class ConversationAccessRole {
    @SerialName("team_member")
    TEAM_MEMBER,
    @SerialName("non_team_member")
    NON_TEAM_MEMBER,
    @SerialName("guest")
    GUEST,
    @SerialName("service")
    SERVICE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
