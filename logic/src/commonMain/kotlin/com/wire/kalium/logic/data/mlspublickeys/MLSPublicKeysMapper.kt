package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.network.api.serverypublickey.MLSPublicKeyItemDTO
import com.wire.kalium.network.api.serverypublickey.MLSPublicKeysDTO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.model.MLSPublicKeyEntity

interface MLSPublicKeysMapper {
    fun fromDTO(publicKeys: MLSPublicKeysDTO): List<MLSPublicKey>
    fun fromDTO(publicKeyItem: MLSPublicKeyItemDTO, keyType: KeyType): MLSPublicKey
    fun toCrypto(publicKey: MLSPublicKey): com.wire.kalium.cryptography.MLSPublicKey
    fun toCrypto(keyType: KeyType): com.wire.kalium.cryptography.KeyType
    fun toEntity(publicKey: MLSPublicKey): MLSPublicKeyEntity
    fun toEntity(keyType: KeyType): com.wire.kalium.persistence.model.KeyType
    fun fromEntity(publicKey: MLSPublicKeyEntity): MLSPublicKey
    fun fromEntity(keyType: com.wire.kalium.persistence.model.KeyType): KeyType
}

class MLSPublicKeysMapperImpl : MLSPublicKeysMapper {
    override fun fromDTO(publicKeys: MLSPublicKeysDTO) = with(publicKeys) {
        removal?.map {
            fromDTO(it, KeyType.REMOVAL)
        } ?: emptyList()
    }

    override fun fromDTO(publicKeyItem: MLSPublicKeyItemDTO, keyType: KeyType) =
        with(publicKeyItem) {
            MLSPublicKey(
                cipherSuite = Conversation.CipherSuite.fromShortName(cipherSuite),
                Key(key),
                keyType
            )
        }

    override fun toCrypto(publicKey: MLSPublicKey) = with(publicKey) {
        com.wire.kalium.cryptography.MLSPublicKey(cipherSuite.name, key.value, toCrypto(keyType))
    }

    override fun toCrypto(keyType: KeyType) = when (keyType) {
        KeyType.REMOVAL -> com.wire.kalium.cryptography.KeyType.REMOVAL
    }

    override fun toEntity(publicKey: MLSPublicKey) = with(publicKey) {
        MLSPublicKeyEntity(
            ConversationEntity.CipherSuite.fromTag(cipherSuite.tag),
            com.wire.kalium.persistence.model.Key(key.value),
            toEntity(keyType)
        )
    }

    override fun toEntity(keyType: KeyType) = when (keyType) {
        KeyType.REMOVAL -> com.wire.kalium.persistence.model.KeyType.REMOVAL
    }

    override fun fromEntity(publicKey: MLSPublicKeyEntity) = with(publicKey) {
        MLSPublicKey(Conversation.CipherSuite.fromTag(cipherSuite.tag), Key(key.value), fromEntity(keyType))
    }

    override fun fromEntity(keyType: com.wire.kalium.persistence.model.KeyType) = when (keyType) {
        com.wire.kalium.persistence.model.KeyType.REMOVAL -> KeyType.REMOVAL
    }
}
