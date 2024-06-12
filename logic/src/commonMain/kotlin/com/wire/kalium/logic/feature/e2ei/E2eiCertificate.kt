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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.WireIdentity
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class E2eiCertificate(
    @SerialName("userHandle")
    var userHandle: String,
    @SerialName("status")
    val status: CertificateStatus,
    @SerialName("serialNumber")
    val serialNumber: String,
    @SerialName("certificateDetail")
    val certificateDetail: String,
    @SerialName("thumbprint")
    val thumbprint: String,
    @SerialName("endAt")
    val endAt: Instant
)

@Serializable
data class MLSClientIdentity(
    // val clientId: ClientId,
    @SerialName("e2eiStatus") val e2eiStatus: MLSClientE2EIStatus,
    @SerialName("thumbprint") val thumbprint: String,
    @SerialName("credentialType") val credentialType: MLSCredentialsType,
    @SerialName("x509Identity") val x509Identity: X509Identity?
) {
    companion object {
        fun fromWireIdentity(identity: WireIdentity): MLSClientIdentity =
            MLSClientIdentity(e2eiStatus = MLSClientE2EIStatus.fromCryptoStatus(identity),
                thumbprint = identity.thumbprint,
                credentialType = MLSCredentialsType.fromCrypto(identity.credentialType),
                x509Identity = identity.x509Identity?.let {
                    X509Identity(
                        //  handle = it.handle,
                        displayName = it.displayName,
                        domain = it.domain,
                        serialNumber = it.serialNumber,
                        certificateDetail = it.certificate,
                        notBefore = Instant.fromEpochSeconds(it.notBefore),
                        notAfter = Instant.fromEpochSeconds(it.notAfter)
                    )
                })
    }
}

@Serializable
data class X509Identity(
    // @SerialName("handle") val handle: Handle,
    @SerialName("displayName") val displayName: String,
    @SerialName("domain") val domain: String,
    @SerialName("serialNumber") val serialNumber: String,
    @SerialName("certificateDetail") val certificateDetail: String,
    @SerialName("notBefore") val notBefore: Instant,
    @SerialName("notAfter") val notAfter: Instant
)

enum class MLSClientE2EIStatus {
    REVOKED, EXPIRED, VALID, NOT_ACTIVATED;

    companion object {
        fun fromCryptoStatus(identity: WireIdentity) =
            if (identity.credentialType == CredentialType.Basic || identity.x509Identity == null)
                NOT_ACTIVATED
            else when (identity.status) {
                CryptoCertificateStatus.REVOKED -> REVOKED
                CryptoCertificateStatus.EXPIRED -> EXPIRED
                CryptoCertificateStatus.VALID -> VALID
            }
    }
}

enum class MLSCredentialsType {
    X509, BASIC;

    companion object {
        fun fromCrypto(value: CredentialType) = when (value) {
            CredentialType.Basic -> BASIC
            CredentialType.X509 -> X509
        }
    }
}
