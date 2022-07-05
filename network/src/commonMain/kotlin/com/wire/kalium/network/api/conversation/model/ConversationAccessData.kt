package com.wire.kalium.network.api.conversation.model

import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
import com.wire.kalium.network.api.notification.EventContentDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class ConversationAccessData(
    @SerialName("access") val access: ConversationAccess,
    @SerialName("access_role_v2") val accessRole: List<ConversationAccessRole>?
)

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged: UpdateConversationAccessResponse()
    @JvmInline
    data class AccessUpdated(val event: EventContentDTO.Conversation): UpdateConversationAccessResponse()
}
