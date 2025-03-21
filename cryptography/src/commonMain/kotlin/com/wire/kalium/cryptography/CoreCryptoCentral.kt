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

import kotlin.time.Duration

interface CoreCryptoCentral {
    suspend fun mlsClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<MLSCiphersuite>,
        defaultCipherSuite: MLSCiphersuite
    ): MLSClient

    suspend fun mlsClient(
        enrollment: E2EIClient,
        certificateChain: CertificateChain,
        newMLSKeyPackageCount: UInt,
        defaultCipherSuite: MLSCiphersuite
    ): MLSClient

    suspend fun proteusClient(): ProteusClient

    /**
     * Enroll Wire E2EIdentity Client for E2EI before MLSClient Initialization
     *
     * @return wire end to end identity client
     */
    @Suppress("LongParameterList")
    suspend fun newAcmeEnrollment(
        clientId: CryptoQualifiedClientId,
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration,
        defaultCipherSuite: MLSCiphersuite
    ): E2EIClient

    /**
     * Register ACME-CA certificates for E2EI
     * @param pem is the certificate string in pem format
     */
    suspend fun registerTrustAnchors(pem: CertificateChain)

    /**
     * Register Certificate Revocations List for an url for E2EI
     * @param url that the CRL downloaded from
     * @param crl fetched crl from the url
     */
    suspend fun registerCrl(url: String, crl: JsonRawData): CrlRegistration

    /**
     * Register Intermediate CA for E2EI
     * @param pem fetched certificate chain in pem format from the CA
     */
    suspend fun registerIntermediateCa(pem: CertificateChain)
}

expect suspend fun coreCryptoCentral(
    rootDir: String,
    databaseKey: String,
    mlsTransporter: MLSTransporter?
): CoreCryptoCentral
