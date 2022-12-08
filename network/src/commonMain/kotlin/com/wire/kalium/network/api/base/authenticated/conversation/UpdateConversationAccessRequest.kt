package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateConversationAccessRequest(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2")
    val accessRole: Set<ConversationAccessRoleDTO>
)

@Serializable
internal data class UpdateConversationAccessRequestV3(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role")
    val accessRole: Set<ConversationAccessRoleDTO>
)
