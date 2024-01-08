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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class MigratedMessage(
    val conversationId: ConversationId,
    val senderUserId: UserId,
    val senderClientId: ClientId,
    val timestamp: Long,
    val content: String,
    val unencryptedProto: ProtoContent?,
    val encryptedProto: ByteArray?,
    val assetName: String?,
    val assetSize: Int?,
    val editTime: Long?
) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MigratedMessage

        if (conversationId != other.conversationId) return false
        if (senderUserId != other.senderUserId) return false
        if (senderClientId != other.senderClientId) return false
        if (timestamp != other.timestamp) return false
        if (content != other.content) return false
        if (encryptedProto != null) {
            if (other.encryptedProto == null) return false
            if (!encryptedProto.contentEquals(other.encryptedProto)) return false
        } else if (other.encryptedProto != null) return false
        if (assetName != other.assetName) return false
        if (assetSize != other.assetSize) return false
        if (editTime != other.editTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conversationId.hashCode()
        result = 31 * result + senderUserId.hashCode()
        result = 31 * result + senderClientId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (encryptedProto?.contentHashCode() ?: 0)
        result = 31 * result + (assetName?.hashCode() ?: 0)
        result = 31 * result + (assetSize ?: 0)
        result = 31 * result + (editTime?.hashCode() ?: 0)
        return result
    }

}
