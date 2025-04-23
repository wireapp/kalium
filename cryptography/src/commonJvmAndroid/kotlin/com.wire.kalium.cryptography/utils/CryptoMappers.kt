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

@file: Suppress("TooManyFunctions")
package com.wire.kalium.cryptography.utils

import com.wire.crypto.BufferedDecryptedMessage
import com.wire.crypto.CRLRegistration
import com.wire.crypto.Ciphersuite
import com.wire.crypto.CommitBundle
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.E2eiConversationState
import com.wire.crypto.MlsGroupInfoEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.crypto.MlsTransportResponse
import com.wire.crypto.PreKey
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CrlRegistration
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.cryptography.GroupInfoBundle
import com.wire.kalium.cryptography.GroupInfoEncryptionType
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.RatchetTreeType
import com.wire.kalium.cryptography.WelcomeBundle
import com.wire.kalium.cryptography.WireIdentity
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64

fun MLSCiphersuite.toCrypto(): Ciphersuite = when (this) {
    MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
    MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
    MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
    MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
}

fun Ciphersuite.toCryptography(): MLSCiphersuite = when (this) {
    Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
    Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 -> MLSCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
    Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 -> MLSCiphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
    Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> MLSCiphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
    Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> MLSCiphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
    Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> MLSCiphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
}

fun com.wire.kalium.cryptography.MlsTransportResponse.toCrypto(): MlsTransportResponse {
    return when (this) {
        com.wire.kalium.cryptography.MlsTransportResponse.Success -> MlsTransportResponse.Success
        com.wire.kalium.cryptography.MlsTransportResponse.Retry -> MlsTransportResponse.Retry
        is com.wire.kalium.cryptography.MlsTransportResponse.Abort -> MlsTransportResponse.Abort(this.reason)
    }
}

fun CommitBundle.toCryptography(): com.wire.kalium.cryptography.CommitBundle = com.wire.kalium.cryptography.CommitBundle(
    commit = commit.value,
    welcome = welcome?.value,
    groupInfoBundle = groupInfoBundle.toCrypto(),
    crlNewDistributionPoints = crlNewDistributionPoints?.lower()
)

fun com.wire.crypto.GroupInfoBundle.toCrypto(): GroupInfoBundle = GroupInfoBundle(
    ratchetTreeType = ratchetTreeType.toCryptography(),
    encryptionType = encryptionType.toCryptography(),
    payload = payload.value
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

fun PreKeyCrypto.toCrypto(): PreKey = PreKey(id.toUShort(), encodedData.decodeBase64Bytes())

fun PreKey.toCryptography(): PreKeyCrypto = PreKeyCrypto(id.toInt(), data.encodeBase64())

fun com.wire.crypto.WelcomeBundle.toCryptography() = WelcomeBundle(
    id.value.encodeBase64(),
    crlNewDistributionPoints?.value?.map { it.toString() }
)

fun toExternalSenderKey(value: ByteArray) = ExternalSenderKey(value)

fun com.wire.crypto.WireIdentity.toCryptography(): WireIdentity? {
    val clientId = CryptoQualifiedClientId.fromEncodedString(clientId)
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

private fun com.wire.crypto.DeviceStatus.toCryptography(): CryptoCertificateStatus = when (this) {
    com.wire.crypto.DeviceStatus.Valid -> CryptoCertificateStatus.VALID
    com.wire.crypto.DeviceStatus.Expired -> CryptoCertificateStatus.EXPIRED
    com.wire.crypto.DeviceStatus.Revoked -> CryptoCertificateStatus.REVOKED
}

fun com.wire.crypto.E2eiConversationState.toCryptography(): E2EIConversationState = when (this) {
    E2eiConversationState.Verified -> E2EIConversationState.VERIFIED
    E2eiConversationState.NotVerified -> E2EIConversationState.NOT_VERIFIED
    E2eiConversationState.NotEnabled -> E2EIConversationState.NOT_ENABLED
}

fun DecryptedMessage.toBundle() = DecryptedMessageBundle(
    message,
    commitDelay,
    senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(it.value) },
    hasEpochChanged,
    identity.toCryptography(),
    crlNewDistributionPoints?.value?.map { it.toString() }
)

fun BufferedDecryptedMessage.toBundle() = DecryptedMessageBundle(
    message,
    commitDelay,
    senderClientId?.let { CryptoQualifiedClientId.fromEncodedString(it.value) },
    hasEpochChanged,
    identity.toCryptography(),
    crlNewDistributionPoints?.value?.map { it.toString() }
)

fun CredentialType.toCrypto() = when (this) {
    CredentialType.Basic -> com.wire.crypto.CredentialType.Basic
    CredentialType.X509 -> com.wire.crypto.CredentialType.X509
}

fun com.wire.crypto.CredentialType.toCryptography() = when (this) {
    com.wire.crypto.CredentialType.Basic -> CredentialType.Basic
    com.wire.crypto.CredentialType.X509 -> CredentialType.X509
}

fun CRLRegistration.toCryptography() = CrlRegistration(
    dirty,
    expiration?.toULong()
)
