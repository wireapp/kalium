/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

fun CipherSuite.toCrypto(): MLSCiphersuite = when (this) {
    CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
    CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
    CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
    CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
    is CipherSuite.UNKNOWN -> MLSCiphersuite.DEFAULT
}

fun MLSCiphersuite.toModel(): CipherSuite = when (this) {
    MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
    MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
    MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
    MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
    else -> CipherSuite.UNKNOWN(0)
}

fun MLSCiphersuite.toDao(): ConversationEntity.CipherSuite = when (this) {
    MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 ->
        ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519

    MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 ->
        ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256

    MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 ->
        ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519

    MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 ->
        ConversationEntity.CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448

    MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 ->
        ConversationEntity.CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521

    MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 ->
        ConversationEntity.CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448

    MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 ->
        ConversationEntity.CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
}
