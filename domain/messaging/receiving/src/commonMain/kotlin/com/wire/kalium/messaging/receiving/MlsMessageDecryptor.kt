/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.messaging.receiving

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.WireIdentity

/**
 * Result of applying one inbound MLS bundle to CoreCrypto state.
 *
 * Proposal delays and CRL distribution points are returned as data. This module never schedules
 * an outgoing commit or performs network-backed certificate processing.
 */
public class MlsDecryptedMessage(
    decryptedMessage: ByteArray?,
    commitDelay: Long?,
    senderClientId: CryptoQualifiedClientId?,
    hasEpochChanged: Boolean,
    identity: WireIdentity?,
    crlNewDistributionPoints: List<String>?
) {
    private val decryptedMessageBytes: ByteArray? = decryptedMessage?.copyOf()
    public val decryptedMessage: ByteArray? get() = decryptedMessageBytes?.copyOf()
    public val commitDelay: Long? = commitDelay
    public val senderClientId: CryptoQualifiedClientId? = senderClientId
    public val hasEpochChanged: Boolean = hasEpochChanged
    public val identity: WireIdentity? = identity
    public val crlNewDistributionPoints: List<String>? = crlNewDistributionPoints?.toList()

    override fun equals(other: Any?): Boolean =
        this === other || other is MlsDecryptedMessage &&
                nullableContentEquals(decryptedMessageBytes, other.decryptedMessageBytes) &&
                commitDelay == other.commitDelay && senderClientId == other.senderClientId &&
                hasEpochChanged == other.hasEpochChanged && identity == other.identity &&
                crlNewDistributionPoints == other.crlNewDistributionPoints

    override fun hashCode(): Int {
        var result = decryptedMessageBytes?.contentHashCode() ?: 0
        result = 31 * result + (commitDelay?.hashCode() ?: 0)
        result = 31 * result + (senderClientId?.hashCode() ?: 0)
        result = 31 * result + hasEpochChanged.hashCode()
        result = 31 * result + (identity?.hashCode() ?: 0)
        result = 31 * result + (crlNewDistributionPoints?.hashCode() ?: 0)
        return result
    }
}

/** Applies inbound MLS application or handshake bytes without sending or recovery side effects. */
public interface MlsMessageDecryptor {
    public suspend fun <Result : Any> decrypt(
        context: MlsCoreCryptoContext,
        message: MlsEncryptedMessage,
        handleDecryptedMessages: suspend (List<MlsDecryptedMessage>) -> Result
    ): Result
}

public class MlsMessageDecryptorImpl : MlsMessageDecryptor {
    override suspend fun <Result : Any> decrypt(
        context: MlsCoreCryptoContext,
        message: MlsEncryptedMessage,
        handleDecryptedMessages: suspend (List<MlsDecryptedMessage>) -> Result
    ): Result {
        val decryptedMessages = context.decryptMessage(message.groupId, message.encryptedMessage).map { decryptedMessage ->
            MlsDecryptedMessage(
                decryptedMessage = decryptedMessage.message,
                commitDelay = decryptedMessage.commitDelay,
                senderClientId = decryptedMessage.senderClientId,
                hasEpochChanged = decryptedMessage.hasEpochChanged,
                identity = decryptedMessage.identity,
                crlNewDistributionPoints = decryptedMessage.crlNewDistributionPoints
            )
        }
        return handleDecryptedMessages(decryptedMessages)
    }
}

private fun nullableContentEquals(first: ByteArray?, second: ByteArray?): Boolean = when {
    first == null -> second == null
    second == null -> false
    else -> first.contentEquals(second)
}
