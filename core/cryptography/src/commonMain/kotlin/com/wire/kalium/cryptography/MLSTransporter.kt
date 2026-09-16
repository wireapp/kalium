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
package com.wire.kalium.cryptography

interface MLSTransporter {
    /**
     * Prepare a history secret before Core Crypto adds it to an outgoing commit.
     *
     * History sharing is deliberately disabled until the application supplies an implementation
     * which protects the secret for transport.
     */
    suspend fun prepareForTransport(historySecret: MLSHistorySecret): ByteArray {
        throw UnsupportedOperationException("MLS history sharing transport is not configured")
    }

    /**
     * Send a commit bundle to the delivery service.
     *
     * Return normally when the delivery service accepts the bundle, or throw
     * [MlsMessageRejectedException] when it rejects it.
     */
    suspend fun sendCommitBundle(commitBundle: CommitBundle)
}

class MLSHistorySecret(
    val clientId: CryptoQualifiedClientId,
    val data: ByteArray
)

/** The delivery service rejected an outgoing MLS commit. */
class MlsMessageRejectedException(
    val reason: String,
    cause: Throwable? = null
) : Exception(reason, cause)
