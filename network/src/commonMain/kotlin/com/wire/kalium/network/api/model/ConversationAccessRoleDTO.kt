package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConversationAccessRoleDTO {
    @SerialName("team_member")
    TEAM_MEMBER,
    @SerialName("non_team_member")
    NON_TEAM_MEMBER,
    @SerialName("guest")
    GUEST,
    @SerialName("service")
    SERVICE,
    @SerialName("partner")
    EXTERNAL;

    override fun toString(): String {
        return this.name.lowercase()
    }

    companion object {
        val DEFAULT_VALUE_WHEN_NULL = setOf(TEAM_MEMBER, NON_TEAM_MEMBER, SERVICE)
    }
}
