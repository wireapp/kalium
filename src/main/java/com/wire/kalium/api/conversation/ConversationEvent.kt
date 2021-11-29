package com.wire.kalium.api.conversation

import com.wire.kalium.models.backend.AccessRole
import com.wire.kalium.models.backend.ConversationId
import com.wire.kalium.models.backend.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationEvent(
        @SerialName("type") val eventType: String,
        @SerialName("data") val data: EventData,
        @Deprecated("use qualified id")
        @SerialName("conversation") val conversationId: String?,
        @SerialName("qualified_conversation") val qualifiedConversationId: ConversationId,
        @Deprecated("use qualified from")
        @SerialName("from") val from: String?,
        @SerialName("qualified_from") val fromUser: UserId,
        @SerialName("time") val time: String
)

@Serializable
data class EventData(
        @SerialName("access") val access: List<AccessRole>, // How users can join conversations = ['private', 'invite', 'link', 'code']
        @SerialName("access_role") val accessRole: String,
        @SerialName("code") val code: String, // Stable conversation identifier ,
        @SerialName("conversation_role") val conversationRole: String?,
        @SerialName("creator") val creator: String,
        @SerialName("data") val `data`: String?, // Extra (symmetric) data (i.e. ciphertext, Base64 in JSON) that is common with all other recipients. ,
        @SerialName("email") val email: String?,
        @SerialName("hidden") val hidden: Boolean?,
        @SerialName("hidden_ref") val hiddenRef: String?,
        @SerialName("id") val id: String?,
        @SerialName("key") val key: String, // Stable conversation identifier
        @SerialName("last_event") val lastEvent: String?,
        @SerialName("last_event_time") val lastEventTime: String?,
        @SerialName("members") val members: ConversationMembersResponse,
        @SerialName("message") val message: String?,
        @SerialName("message_timer") val messageTimer: Int?, // Per-conversation message timer (can be null)
        @SerialName("name") val name: String,
        @SerialName("otr_archived") val otrArchived: Boolean?,
        @SerialName("otr_archived_ref") val otrArchivedRef: String?,
        @SerialName("otr_muted_ref") val otrMutedRef: String?,
        @SerialName("otr_muted_status") val otrMutedStatus: Int?,
        @SerialName("qualified_id") val qualifiedId: ConversationId,
        @SerialName("qualified_recipient") val qualifiedRecipient: UserId,
        @SerialName("qualified_target") val qualifiedTarget: UserId,
        @SerialName("qualified_user_ids") val qualifiedUserIds: List<UserId>,
        @SerialName("receipt_mode") val receiptMode: Int, //Conversation receipt mode
        @SerialName("recipient") val recipient: String,
        @SerialName("sender") val sender: String,
        @SerialName("status") val status: String, //  ['started', 'stopped']
        @SerialName("target") val target: String?,
        @SerialName("team") val team: String?,
        @SerialName("text") val text: String, // The ciphertext for the recipient (Base64 in JSON)
        @SerialName("type") val type: Int, //  ['0', '1', '2', '3']
        @SerialName("uri") val uri: String?,
        @SerialName("users") val users: List<SimpleMember>
)

@Serializable
data class SimpleMember(
        @SerialName("conversation_role") val conversationRole: String?, // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed by Wire (i.e., no custom roles can have the same prefix)
        @SerialName("qualified_id") val qualifiedId: UserId
)
