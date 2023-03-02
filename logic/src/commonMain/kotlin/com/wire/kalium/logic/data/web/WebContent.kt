/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
sealed class WebContent {

    @Serializable
    sealed class Conversation : WebContent() {

        @Serializable
        @SerialName("conversation.group-creation")
        data class NewGroup(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("data") val members: WebGroupMembers,
            val time: String,
        ) : Conversation()

        @Serializable
        @SerialName("conversation.message-add")
        data class TextMessage(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String,
            val time: String,
            val id: String,
            @SerialName("data") val data: WebTextData,
            @SerialName("reactions") val reactions: Map<String, String>?
        ) : Conversation()

        @Serializable
        @SerialName("conversation.asset-add")
        data class AssetMessage(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String,
            val time: String,
            val id: String,
            @SerialName("data") val data: WebAssetData,
            @SerialName("reactions") val reactions: Map<String, String>?
        ) : Conversation()

        @Serializable
        @SerialName("conversation.knock")
        data class KnockMessage(
            @SerialName("qualified_conversation") val qualifiedConversation: ConversationId,
            @SerialName("qualified_from") val qualifiedFrom: UserId?,
            @SerialName("from") val from: String,
            @SerialName("from_client_id") val fromClientId: String,
            val time: String,
            val id: String,
            @SerialName("data") val data: WebKnockData
        ) : Conversation()
    }
}

@Serializable
data class WebGroupMembers(
    val allTeamMembers: Boolean,
    val name: String,
    val userIds: List<UserId>
)


@Serializable
data class WebTextData(
    @SerialName("content") val text: String,
//     @SerialName("quote") val quote: WebTextQuote?,
//     @SerialName("mentions") val mentions: List<String>?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("legal_hold_status") val legalHoldStatus: Int?
)

@Serializable
data class WebTextQuote(
    @SerialName("message_id") val messageId: String?,
    @SerialName("user_id") val userId: String?,
)

@Serializable
data class WebAssetData(
    @SerialName("content_length") val contentLength: Int?,
    @SerialName("content_type") val contentType: String?,
    val domain: String?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    val info: WebAssetInfo?,
    val key: String?,
    @SerialName("legal_hold_status") val legalHoldStatus: Int,
    @SerialName("otr_key") val otrKey: JsonObject?,
    @SerialName("sha256") val sha256: JsonObject?,
    val status: String?,
    val token: String?
)

@Serializable
data class WebKnockData(
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("legal_hold_status") val legalHoldStatus: Int,
)

@Serializable
data class WebAssetInfo(
    val height: Int?,
    val name: String?,
    val tag: String?,
    val width: Int?
)
