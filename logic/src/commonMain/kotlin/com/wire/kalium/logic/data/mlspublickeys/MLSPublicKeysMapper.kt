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

package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO
import io.ktor.util.decodeBase64Bytes

interface MLSPublicKeysMapper {
    fun fromDTO(publicKeys: MLSPublicKeysDTO): List<MLSPublicKey>
    fun toCrypto(publicKey: MLSPublicKey): com.wire.kalium.cryptography.Ed22519Key
    fun toCrypto(externalSenderKey: ExternalSenderKey): com.wire.kalium.cryptography.Ed22519Key
}

class MLSPublicKeysMapperImpl : MLSPublicKeysMapper {
    override fun fromDTO(publicKeys: MLSPublicKeysDTO) = with(publicKeys) {
        removal?.entries?.mapNotNull {
            when (it.key) {
                ED25519 -> MLSPublicKey(Ed25519Key(it.value.decodeBase64Bytes()), KeyType.REMOVAL)
                else -> null
            }
        } ?: emptyList()
    }

    override fun toCrypto(publicKey: MLSPublicKey) = with(publicKey) {
        com.wire.kalium.cryptography.Ed22519Key(key.value)
    }

    override fun toCrypto(externalSenderKey: ExternalSenderKey) = with(externalSenderKey) {
        com.wire.kalium.cryptography.Ed22519Key(this.value)
    }

    companion object {
        const val ED25519 = "ed25519"
    }

}
