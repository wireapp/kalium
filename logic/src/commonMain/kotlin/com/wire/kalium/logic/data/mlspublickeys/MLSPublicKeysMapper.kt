package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO
import io.ktor.util.decodeBase64Bytes

interface MLSPublicKeysMapper {
    fun fromDTO(publicKeys: MLSPublicKeysDTO): List<MLSPublicKey>
    fun toCrypto(publicKey: MLSPublicKey): com.wire.kalium.cryptography.Ed22519Key
}

class MLSPublicKeysMapperImpl : MLSPublicKeysMapper {
    override fun fromDTO(publicKeys: MLSPublicKeysDTO) = with(publicKeys) {
        removal?.entries?.mapNotNull {
            when (it.key) {
                ED22519 -> MLSPublicKey(Ed25519Key(it.value.decodeBase64Bytes()), KeyType.REMOVAL)
                else -> null
            }
        } ?: emptyList()
    }

    override fun toCrypto(publicKey: MLSPublicKey) = with(publicKey) {
        com.wire.kalium.cryptography.Ed22519Key(key.value)
    }

    companion object {
        const val ED22519 = "ed22519"
    }

}
