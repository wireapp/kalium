/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network.api.authenticated.nomaddevice

import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NomadMessageEventsRequest(
    @SerialName("events")
    val events: List<NomadMessageEvent>
)

@Serializable
sealed interface NomadMessageEvent {

    @Serializable
    @SerialName("UPSERT_MESSAGE")
    data class UpsertMessageEvent(
        @SerialName("message_id")
        val messageId: String,
        @SerialName("conversation")
        val conversation: Conversation,
        @SerialName("timestamp")
        val timestamp: Long,
        @SerialName("payload")
        val payload: String
    ) : NomadMessageEvent

    @Serializable
    @SerialName("UPSERT_MESSAGE_STATUS")
    data class UpsertMessageStatusEvent(
        @SerialName("message_id")
        val messageId: String,
        @SerialName("conversation")
        val conversation: Conversation,
        @SerialName("reaction")
        val reaction: ReactionsPayload? = null,
        @SerialName("read_receipt")
        val readReceipt: ReadReceiptsPayload? = null
    ) : NomadMessageEvent {
        init {
            require((reaction == null) xor (readReceipt == null)) {
                "Exactly one of reaction or read_receipt must be set."
            }
        }
    }

    @Serializable
    @SerialName("DELETE_MESSAGE")
    data class DeleteMessageEvent(
        @SerialName("conversation")
        val conversation: Conversation,
        @SerialName("message_id")
        val messageId: String
    ) : NomadMessageEvent

    @Serializable
    @SerialName("WIPE_CONVERSATION")
    data class WipeConversationEvent(
        @SerialName("conversation")
        val conversation: Conversation,
        @SerialName("wipe_meta_data")
        val wipeMetaData: Boolean
    ) : NomadMessageEvent

    @Serializable
    @SerialName("LAST_READ")
    data class LastReadEvent(
        @SerialName("last_read")
        val lastRead: List<LastRead>
    ) : NomadMessageEvent {
        init {
            require(lastRead.isNotEmpty()) { "last_read must not be empty." }
        }
    }
}

@Serializable
data class LastRead(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("last_read")
    val lastReadTimestamp: Long
)

@Serializable
data class Conversation(
    @SerialName("id")
    val id: String,
    @SerialName("domain")
    val domain: String
)

@Serializable
data class ReactionsPayload(
    @SerialName("reactions_by_user")
    val reactionsByUser: List<ReactionByUser>
)

@Serializable
data class ReactionByUser(
    @SerialName("user_id")
    val userId: QualifiedID,
    @SerialName("emojis")
    val emojis: List<String>
)

@Serializable
data class ReadReceiptsPayload(
    @SerialName("read_receipts")
    val readReceipts: List<ReadReceiptEntry>
)

@Serializable
data class ReadReceiptEntry(
    @SerialName("user_id")
    val userId: QualifiedID,
    @SerialName("date")
    val date: String
)
