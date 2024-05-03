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

import com.wire.kalium.protobuf.messages.EncryptionAlgorithm

class EncryptionAlgorithmMapper {

    fun fromProtobufModel(encryptionAlgorithm: EncryptionAlgorithm?): MessageEncryptionAlgorithm? =
        when (encryptionAlgorithm) {
            EncryptionAlgorithm.AES_CBC -> MessageEncryptionAlgorithm.AES_CBC
            EncryptionAlgorithm.AES_GCM -> MessageEncryptionAlgorithm.AES_GCM
            else -> null
        }

    fun toProtoBufModel(messageEncryptionAlgorithm: MessageEncryptionAlgorithm?): EncryptionAlgorithm? =
        when (messageEncryptionAlgorithm) {
            MessageEncryptionAlgorithm.AES_CBC -> EncryptionAlgorithm.AES_CBC
            MessageEncryptionAlgorithm.AES_GCM -> EncryptionAlgorithm.AES_GCM
            else -> null
        }
}
