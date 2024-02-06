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
    suspend fun mlsClient(clientId: CryptoQualifiedClientId): MLSClient

    suspend fun mlsClient(enrollment: E2EIClient, certificateChain: CertificateChain, newMLSKeyPackageCount: UInt): MLSClient

    suspend fun proteusClient(): ProteusClient

    /**
     * Enroll Wire E2EIdentity Client for E2EI before MLSClient Initialization
     *
     * @return wire end to end identity client
     */
    suspend fun newAcmeEnrollment(
        clientId: CryptoQualifiedClientId,
        displayName: String,
        handle: String,
        teamId: String?,
        expiry: Duration
    ): E2EIClient
}

expect suspend fun coreCryptoCentral(rootDir: String, databaseKey: String): CoreCryptoCentral
