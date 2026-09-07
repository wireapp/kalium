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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.cryptography.utils

import com.wire.crypto.BufferedDecryptedMessage
import com.wire.crypto.CipherSuite
import com.wire.crypto.ClientId
import com.wire.crypto.CommitBundle
import com.wire.crypto.ConversationId
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.DeviceStatus
import com.wire.crypto.E2eiConversationState
import com.wire.crypto.MlsGroupInfoEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.ProteusAutoPrekeyBundle
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.WireIdentity
import kotlin.io.encoding.Base64

fun MLSCiphersuite.toCrypto(): CipherSuite = when (this) {
    MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
    MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519
    MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448
    MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448
    MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
}

fun CipherSuite.toCryptography(): MLSCiphersuite = when (this) {
    CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
    CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448 -> MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
    CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448 -> MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
    CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
}

fun CommitBundle.toCryptography(): com.wire.kalium.cryptography.CommitBundle = com.wire.kalium.cryptography.CommitBundle(
    commit = commit,
    welcome = welcome?.serialize(),
    groupInfoBundle = groupInfo.toCrypto(),
    encryptedMessage = encryptedMessage
)

fun com.wire.crypto.GroupInfoBundle.toCrypto(): GroupInfoBundle = GroupInfoBundle(
    ratchetTreeType = ratchetTreeType.toCryptography(),
    encryptionType = encryptionType.toCryptography(),
    payload = payload
)

fun MlsRatchetTreeType.toCryptography(): RatchetTreeType = when (this) {
    MlsRatchetTreeType.FULL -> RatchetTreeType.FULL
    MlsRatchetTreeType.DELTA -> RatchetTreeType.DELTA
    MlsRatchetTreeType.BY_REF -> RatchetTreeType.BY_REF
}

fun MlsGroupInfoEncryptionType.toCryptography(): GroupInfoEncryptionType = when (this) {
    MlsGroupInfoEncryptionType.PLAINTEXT -> GroupInfoEncryptionType.PLAINTEXT
    MlsGroupInfoEncryptionType.JWE_ENCRYPTED -> GroupInfoEncryptionType.JWE_ENCRYPTED
}

fun PreKeyCrypto.toCrypto(): ProteusAutoPrekeyBundle = ProteusAutoPrekeyBundle(id.toUShort(), Base64.decode(pkb))

fun ProteusAutoPrekeyBundle.toCryptography(): PreKeyCrypto = PreKeyCrypto(id.toInt(), Base64.encode(pkb))

fun com.wire.crypto.WireIdentity.toCryptography(): WireIdentity? {
    val clientId = clientId?.toCryptography()
    return clientId?.let { qualifiedClientId ->
        WireIdentity(
            qualifiedClientId,
            status.toCryptography(),
            thumbprint,
            credentialType.toCryptography(),
            x509Identity?.toCryptography()
        )
    }
}

fun com.wire.crypto.X509Identity.toCryptography() = WireIdentity.X509Identity(
    handle = WireIdentity.Handle.fromString(handle, domain),
    displayName = displayName,
    domain = domain,
    certificate = certificate,
    serialNumber = serialNumber,
    notBefore = notBefore.epochSecond,
    notAfter = notAfter.epochSecond
)

private val kotlinx.datetime.Instant.epochSecond: Long
    get() = this.epochSeconds

private fun DeviceStatus.toCryptography(): CryptoCertificateStatus = when (this) {
    DeviceStatus.VALID -> CryptoCertificateStatus.VALID
    DeviceStatus.EXPIRED -> CryptoCertificateStatus.EXPIRED
    DeviceStatus.REVOKED -> CryptoCertificateStatus.REVOKED
}

fun E2eiConversationState.toCryptography(): E2EIConversationState = when (this) {
    E2eiConversationState.VERIFIED -> E2EIConversationState.VERIFIED
    E2eiConversationState.NOT_VERIFIED -> E2EIConversationState.NOT_VERIFIED
    E2eiConversationState.NOT_ENABLED -> E2EIConversationState.NOT_ENABLED
}

fun DecryptedMessage.toBundle(): DecryptedMessageBundle = when (this) {
    is DecryptedMessage.Text -> DecryptedMessageBundle.Text(
        message = plaintext,
        senderClientId = senderClientId.toCryptography(),
        identity = identity.toCryptography()
    )

    is DecryptedMessage.Commit -> DecryptedMessageBundle.Commit(
        isActive = isActive,
        identity = identity.toCryptography()
    )

    is DecryptedMessage.Proposal -> DecryptedMessageBundle.Proposal(
        commitDelay = delay?.toLong(),
        identity = identity.toCryptography()
    )
}

fun BufferedDecryptedMessage.toBundle(): DecryptedMessageBundle = when (this) {
    is BufferedDecryptedMessage.Text -> DecryptedMessageBundle.Text(
        message = plaintext,
        senderClientId = senderClientId.toCryptography(),
        identity = identity.toCryptography()
    )

    is BufferedDecryptedMessage.Commit -> DecryptedMessageBundle.Commit(
        isActive = isActive,
        identity = identity.toCryptography()
    )

    is BufferedDecryptedMessage.Proposal -> DecryptedMessageBundle.Proposal(
        commitDelay = delay?.toLong(),
        identity = identity.toCryptography()
    )
}

fun CredentialType.toCrypto() = when (this) {
    CredentialType.Basic -> com.wire.crypto.CredentialType.BASIC
    CredentialType.X509 -> com.wire.crypto.CredentialType.X509
}

fun com.wire.crypto.CredentialType.toCryptography() = when (this) {
    com.wire.crypto.CredentialType.BASIC -> CredentialType.Basic
    com.wire.crypto.CredentialType.X509 -> CredentialType.X509
}

fun MLSGroupId.toCrypto() = com.wire.crypto.ConversationId(Base64.decode(this))

fun ConversationId.toCryptography(): MLSGroupId = Base64.encode(copyBytes())

fun ClientId.toCryptography(): CryptoQualifiedClientId {
    val deserialized = deserialize()
    return try {
        val deviceId = deserialized.deviceId.toHexString().trimStart('0').ifEmpty { "0" }
        CryptoQualifiedClientId(
            value = deviceId,
            userId = CryptoQualifiedID(deserialized.userId.toString(), deserialized.domain)
        )
    } finally {
        deserialized.destroy()
    }
}
