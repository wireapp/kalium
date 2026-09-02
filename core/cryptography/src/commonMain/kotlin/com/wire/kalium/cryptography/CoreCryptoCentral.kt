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
package com.wire.kalium.cryptography

import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

@Suppress("LongParameterList", "TooManyFunctions")
interface CoreCryptoCentral {
    suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope
    ): MLSClient

    suspend fun proteusClient(): ProteusClient

    /**
     * Creates and installs a PKI environment backed by the same encrypted database as Core Crypto.
     */
    suspend fun configurePkiEnvironment(hooks: PkiEnvironmentHooks)

    suspend fun getPkiTrustAnchors(): List<CertificateChain>

    /**
     * Adds every certificate from [pemBundle] that is not already installed.
     * Existing trust anchors are never removed.
     */
    suspend fun addPkiTrustAnchors(pemBundle: CertificateChain)

    suspend fun addPkiIntermediateCertificate(pem: CertificateChain)

    /**
     * Starts an X509 acquisition and keeps it alive while [PkiEnvironmentHooks.authenticate] performs authentication.
     * Consumes [existingCredentialRef].
     */
    suspend fun startX509CredentialAcquisition(
        config: X509CredentialAcquisitionConfig,
        existingCredentialRef: CryptoCredentialRef? = null
    ): CryptoCredential

    /** Persists and consumes [credential]. */
    suspend fun installCredential(credential: CryptoCredential): CryptoCredentialRef

    /** Check all installed X509 credentials for expiration and revocation. */
    suspend fun checkCredentials()

    /**
     * Export a compacted copy of the CoreCrypto database to the specified destination path.
     * Uses SQLite's VACUUM INTO to create an encrypted copy of the database.
     *
     * @param destinationPath the path where the database copy should be created
     * @throws Exception if the export operation fails
     */
    suspend fun exportDatabaseCopy(destinationPath: String)

    /** Close Core Crypto, its PKI environment, and its database. */
    suspend fun close()
}

enum class PkiHttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD
}

data class PkiHttpHeader(
    val name: String,
    val value: String
)

data class PkiHttpResponse(
    val status: UShort,
    val headers: List<PkiHttpHeader>,
    val body: ByteArray
)

/** Core-Crypto-neutral callbacks used by the v10 PKI environment. */
interface PkiEnvironmentHooks {
    suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray
    ): PkiHttpResponse

    suspend fun authenticate(
        idp: String,
        keyAuth: String,
        acmeAud: String
    ): String

    suspend fun getBackendNonce(): String

    suspend fun fetchBackendAccessToken(dpop: String): String
}

@Suppress("LongParameterList")
data class X509CredentialAcquisitionConfig(
    val acmeDirectoryUrl: String,
    val cipherSuite: MLSCiphersuite,
    val displayName: String,
    val clientId: CryptoQualifiedClientId,
    val handle: String,
    val teamId: String?,
    val validity: Duration
)

expect suspend fun coreCryptoCentral(
    rootDir: String,
    passphrase: ByteArray,
): CoreCryptoCentral
