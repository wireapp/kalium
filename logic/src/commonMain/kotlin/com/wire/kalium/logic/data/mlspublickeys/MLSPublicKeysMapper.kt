package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO

interface MLSPublicKeysMapper {
    fun fromDTO(publicKeys: MLSPublicKeysDTO): List<MLSPublicKey>
    fun toCrypto(publicKey: MLSPublicKey): com.wire.kalium.cryptography.MLSPublicKey
    fun toCrypto(keyType: KeyType): com.wire.kalium.cryptography.KeyType
}

class MLSPublicKeysMapperImpl : MLSPublicKeysMapper {
    override fun fromDTO(publicKeys: MLSPublicKeysDTO) = with(publicKeys) {
        removal?.entries?.map {
            MLSPublicKey(
                cipherSuite = Conversation.CipherSuite.fromShortName(it.key),
                Key(it.value),
                KeyType.REMOVAL
            )
        } ?: emptyList()
    }

    override fun toCrypto(publicKey: MLSPublicKey) = with(publicKey) {
        com.wire.kalium.cryptography.MLSPublicKey(cipherSuite.name, key.value, toCrypto(keyType))
    }

    override fun toCrypto(keyType: KeyType) = when (keyType) {
        KeyType.REMOVAL -> com.wire.kalium.cryptography.KeyType.REMOVAL
    }
}
