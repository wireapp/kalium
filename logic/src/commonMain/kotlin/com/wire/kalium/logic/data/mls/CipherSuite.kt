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
package com.wire.kalium.logic.data.mls

import com.wire.kalium.network.api.base.authenticated.client.MLSPublicKeyTypeDTO

data class SupportedCipherSuite(
    val supported: List<CipherSuite>,
    val default: CipherSuite
)

@Suppress("MagicNumber", "ClassName")
sealed class CipherSuite(open val tag: Int) {
    data class UNKNOWN(override val tag: Int) : CipherSuite(tag) {
        override fun toString(): String {
            return "UNKNOWN($tag)".uppercase()
        }
    }

    data object MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 : CipherSuite(1) {
        override fun toString(): String {
            return "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519"
        }
    }

    data object MLS_128_DHKEMP256_AES128GCM_SHA256_P256 : CipherSuite(2) {
        override fun toString(): String {
            return "MLS_128_DHKEMP256_AES128GCM_SHA256_P256"
        }
    }

    data object MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 :
        CipherSuite(3) {
        override fun toString(): String {
            return "MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519"
        }
    }

    data object MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 : CipherSuite(4) {
        override fun toString(): String {
            return "MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448"

        }
    }

    data object MLS_256_DHKEMP521_AES256GCM_SHA512_P521 : CipherSuite(5) {
        override fun toString(): String {
            return "MLS_256_DHKEMP521_AES256GCM_SHA512_P521"
        }

    }

    data object MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 : CipherSuite(6) {
        override fun toString(): String {
            return "MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448"
        }
    }

    data object MLS_256_DHKEMP384_AES256GCM_SHA384_P384 : CipherSuite(7) {
        override fun toString(): String {
            return "MLS_256_DHKEMP384_AES256GCM_SHA384_P384"
        }
    }

    data object MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519 :
        CipherSuite(61489) {
        override fun toString(): String {
            return "MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519"
        }
    }

    companion object {
        fun fromTag(tag: Int): CipherSuite = when (tag) {
            1 -> MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            2 -> MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            3 -> MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
            4 -> MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
            5 -> MLS_256_DHKEMP521_AES256GCM_SHA512_P521
            6 -> MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
            7 -> MLS_256_DHKEMP384_AES256GCM_SHA384_P384
            61489 -> MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519
            else -> UNKNOWN(tag)
        }

        fun fromTag(tag: UShort) = fromTag(tag.toInt())
    }
}

fun CipherSuite.signatureAlgorithm(): MLSPublicKeyTypeDTO? = when (this) {
    CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> MLSPublicKeyTypeDTO.ECDSA_SECP256R1_SHA256
    CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> MLSPublicKeyTypeDTO.ED25519
    CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> MLSPublicKeyTypeDTO.ED25519
    CipherSuite.MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519 -> MLSPublicKeyTypeDTO.ED25519
    CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> MLSPublicKeyTypeDTO.ECDSA_SECP384R1_SHA384
    CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> MLSPublicKeyTypeDTO.ECDSA_SECP521R1_SHA512
    CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> MLSPublicKeyTypeDTO.ED448
    CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> MLSPublicKeyTypeDTO.ED448
    is CipherSuite.UNKNOWN -> null
}
