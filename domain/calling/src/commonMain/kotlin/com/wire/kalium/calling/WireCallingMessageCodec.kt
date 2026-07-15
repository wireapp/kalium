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

package com.wire.kalium.calling

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.QualifiedConversationId

/** Shared protobuf and external-payload codec for client and service calling transport. */
object WireCallingMessageCodec {
    fun encode(
        messageId: String,
        content: String,
        callHostConversationId: QualifiedID,
    ): ByteArray = GenericMessage(
        messageId = messageId,
        content = GenericMessage.Content.Calling(
            Calling(
                content = content,
                qualifiedConversationId = QualifiedConversationId(
                    callHostConversationId.value,
                    callHostConversationId.domain,
                ),
            ),
        ),
    ).encodeToByteArray()

    /** Resolves at most one Proteus external-message envelope and returns readable protobuf bytes. */
    fun resolveExternal(plaintext: ByteArray, encryptedExternal: ByteArray?): ByteArray {
        val message = GenericMessage.decodeFromByteArray(plaintext)
        val external = message.external ?: return plaintext
        val encrypted = requireNotNull(encryptedExternal) {
            "Proteus external message instructions are missing their encrypted payload"
        }
        return decryptDataWithAES256(
            EncryptedData(encrypted),
            AES256Key(external.otrKey.array),
        ).data.also { readable ->
            require(GenericMessage.decodeFromByteArray(readable).external == null) {
                "Nested external Proteus messages are not supported"
            }
        }
    }
}
