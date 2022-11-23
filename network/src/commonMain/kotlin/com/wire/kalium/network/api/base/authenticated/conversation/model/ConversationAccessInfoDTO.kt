package com.wire.kalium.network.api.base.authenticated.conversation.model

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * **Deprecation info**: Since API v3 `access_role_v2` is deprecated and will be replaced by `access_role`, but until all servers have
 * been upgraded to have API v3 has its minimum supported version and all previously stored events have expired we need support both cases.
 *
 * Further info: https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/672006169/API+changes+v2+v3
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ConversationAccessInfoDTO constructor(
    @SerialName("access")
    val access: Set<ConversationAccessDTO>,
    @SerialName("access_role_v2") @JsonNames("access_role")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}
