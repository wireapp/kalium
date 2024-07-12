/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.data.web

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WebConversationContent(
    @SerialName("id") val id: String,
    @SerialName("type") val type: Int,
    @SerialName("name") val name: String?,
    @SerialName("muted_state") val mutedState: Int?,
    @SerialName("access_role") val accessRole: List<String>?,
    @SerialName("access") val access: List<String>?,
    @SerialName("archived_state") val archivedState: Boolean?,
    @SerialName("archived_timestamp") val archivedTimestamp: Long?,
    @SerialName("cleared_timestamp") val clearedTimestamp: Long?,
    @SerialName("creator") val creator: String?,
    @SerialName("domain") val domain: String?,
    @SerialName("epoch") val epoch: Int?,
    @SerialName("receipt_mode") val receiptMode: Int?,
    @SerialName("is_guest") val isGuest: Boolean?,
    @SerialName("is_managed") val isManaged: Boolean?,
    @SerialName("last_event_timestamp") val lastEventTimestamp: Long?,
    @SerialName("last_read_timestamp") val lastReadTimestamp: Long?,
    @SerialName("last_server_timestamp") val lastServerTimestamp: Long?,
    @SerialName("legal_hold_status") val legalHoldStatus: Int?,
    @SerialName("muted_timestamp") val mutedTimestamp: Long?,
    @SerialName("others") val others: List<String>?,
    @SerialName("protocol") val protocol: String?,
    @SerialName("status") val status: Int?,
    @SerialName("team_id") val teamId: String?,
    @SerialName("ephemeral_timer") val messageTimer: Long?
)

@Serializable
sealed interface WebEventContent {

    @Serializable
    sealed interface Conversation : WebEventContent {
        val qualifiedConversation: ConversationId
        val conversation: String
        val qualifiedFrom: UserId?

        @Serializable
        @SerialName("conversation.group-creation")
        data class NewGroup(
            @SerialName("qualified_conversation") override val qualifiedConversation: ConversationId,
            @SerialName("conversation") override val conversation: String,
            @SerialName("qualified_from") override val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("data") val members: WebGroupMembers,
            @SerialName("time") val time: String
        ) : Conversation

        @Serializable
        @SerialName("conversation.message-add")
        data class TextMessage(
            @SerialName("qualified_conversation") override val qualifiedConversation: ConversationId,
            @SerialName("conversation") override val conversation: String,
            @SerialName("qualified_from") override val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String?,
            @SerialName("time") val time: String,
            @SerialName("id") val id: String,
            @SerialName("data") val data: WebTextData,
            @SerialName("reactions") val reactions: Map<String, String>?,
            @SerialName("category") val category: Int? // 16 ?
        ) : Conversation

        @Serializable
        @SerialName("conversation.asset-add")
        data class AssetMessage(
            @SerialName("qualified_conversation") override val qualifiedConversation: ConversationId,
            @SerialName("conversation") override val conversation: String,
            @SerialName("qualified_from") override val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String?,
            @SerialName("time") val time: String,
            @SerialName("id") val id: String,
            @SerialName("data") val data: WebAssetData,
            @SerialName("reactions") val reactions: Map<String, String>?
        ) : Conversation

        @Serializable
        @SerialName("conversation.knock")
        data class KnockMessage(
            @SerialName("qualified_conversation") override val qualifiedConversation: ConversationId,
            @SerialName("conversation") override val conversation: String,
            @SerialName("qualified_from") override val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String,
            @SerialName("time") val time: String,
            @SerialName("id") val id: String,
            @SerialName("data") val data: WebKnockData
        ) : Conversation
    }

    @Serializable
    @SerialName("unknown")
    data object Unknown : WebEventContent
}

@Serializable
data class WebGroupMembers(
    @SerialName("allTeamMembers") val allTeamMembers: Boolean,
    @SerialName("name") val name: String,
    @SerialName("userIds") val userIds: List<UserId>
)

@Serializable
data class WebTextData(
    @SerialName("content") val text: String,
//     @SerialName("quote") val quote: WebTextQuote?,
//     @SerialName("mentions") val mentions: List<String>?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean?,
    @SerialName("legal_hold_status") val legalHoldStatus: Int?
)

@Serializable
data class WebTextQuote(
    @SerialName("message_id") val messageId: String?,
    @SerialName("user_id") val userId: String?,
)

@Serializable
data class WebAssetData(
    @SerialName("content_length") val contentLength: Long?,
    @SerialName("content_type") val contentType: String?,
    @SerialName("domain") val domain: String?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("info") val info: WebAssetInfo?,
    @SerialName("key") val key: String?,
    @SerialName("legal_hold_status") val legalHoldStatus: Int,
    @SerialName("otr_key") val otrKey: Map<String, Int>?,
    @SerialName("sha256") val sha256: Map<String, Int>?,
    @SerialName("status") val status: String?,
    @SerialName("token") val token: String?,
    @SerialName("meta") val meta: WebAssetMeta?
)

@Serializable
data class WebKnockData(
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("legal_hold_status") val legalHoldStatus: Int
)

@Serializable
data class WebAssetInfo(
    @SerialName("height") val height: String?,
    @SerialName("name") val name: String?,
    @SerialName("tag") val tag: String?,
    @SerialName("width") val width: String?
)

@Serializable
data class WebAssetMeta(
    @SerialName("duration") val duration: Long?,
    @SerialName("loudness") val loudness: JsonObject?
)
