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

import com.wire.kalium.logic.data.mls.CipherSuite

interface MLSPublicKeysMapper {
    fun fromCipherSuite(cipherSuite: CipherSuite): MLSPublicKeyType
}

class MLSPublicKeysMapperImpl : MLSPublicKeysMapper {

    override fun fromCipherSuite(cipherSuite: CipherSuite): MLSPublicKeyType {
        return when (cipherSuite) {
            CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> MLSPublicKeyType.ECDSA_SECP256R1_SHA256
            CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> MLSPublicKeyType.ED25519
            CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> MLSPublicKeyType.ED25519
            CipherSuite.MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519 -> MLSPublicKeyType.ED25519
            CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> MLSPublicKeyType.ECDSA_SECP384R1_SHA384
            CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> MLSPublicKeyType.ECDSA_SECP521R1_SHA512
            CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> MLSPublicKeyType.ED448
            CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> MLSPublicKeyType.ED448
            is CipherSuite.UNKNOWN -> MLSPublicKeyType.Unknown(null)
        }
    }
}

@Suppress("ClassNaming")
sealed interface MLSPublicKeyType {
    val value: String?

    data object ECDSA_SECP256R1_SHA256 : MLSPublicKeyType() {
        override val value: String = "ecdsa_secp256r1_sha256"
    }

    data object ECDSA_SECP384R1_SHA384 : MLSPublicKeyType() {
        override val value: String = "ecdsa_secp384r1_sha384"
    }

    data object ECDSA_SECP521R1_SHA512 : MLSPublicKeyType() {
        override val value: String = "ecdsa_secp521r1_sha512"
    }

    data object ED448 : MLSPublicKeyType() {
        override val value: String = "ed448"
    }

    data object ED25519 : MLSPublicKeyType() {
        override val value: String = "ed25519"
    }

    data class Unknown(override val value: String?) : MLSPublicKeyType()

    companion object {
        fun from(value: String) = when (value) {
            ECDSA_SECP256R1_SHA256.value -> ECDSA_SECP256R1_SHA256
            ECDSA_SECP384R1_SHA384.value -> ECDSA_SECP384R1_SHA384
            ECDSA_SECP521R1_SHA512.value -> ECDSA_SECP521R1_SHA512
            ED448.value -> ED448
            ED25519.value -> ED25519
            else -> Unknown(value)
        }
    }
}
