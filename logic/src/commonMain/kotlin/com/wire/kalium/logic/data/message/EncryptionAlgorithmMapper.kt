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
