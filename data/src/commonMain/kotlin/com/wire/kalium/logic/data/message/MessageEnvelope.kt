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
import com.wire.kalium.logic.data.user.UserId

data class MessageEnvelope(
    val senderClientId: ClientId,
    val recipients: List<RecipientEntry>,
    val dataBlob: EncryptedMessageBlob? = null
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is MessageEnvelope
                && other.senderClientId == senderClientId
                && other.recipients == recipients
                && other.dataBlob?.data.contentEquals(dataBlob?.data))

    override fun hashCode(): Int {
        var result = senderClientId.hashCode()
        result = HASH_MULTIPLIER * result + recipients.hashCode()
        result = HASH_MULTIPLIER * result + (dataBlob?.data?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        private const val HASH_MULTIPLIER = 31
    }
}

data class RecipientEntry(val userId: UserId, val clientPayloads: List<ClientPayload>)

data class ClientPayload(val clientId: ClientId, val payload: EncryptedMessageBlob) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is ClientPayload
                && other.clientId == clientId
                && other.payload.data.contentEquals(payload.data))

    override fun hashCode(): Int = HASH_MULTIPLIER * clientId.hashCode() + payload.data.contentHashCode()

    companion object {
        private const val HASH_MULTIPLIER = 31
    }
}
