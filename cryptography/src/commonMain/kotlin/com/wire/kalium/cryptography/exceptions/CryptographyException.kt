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
package com.wire.kalium.cryptography.exceptions

class CryptographyException(override val message: String, val rootCause: Throwable? = null) : Exception(message, rootCause)

sealed class CryptographyMLSException : Exception() {
    class ConversationAlreadyExists(
        val conversationId: ByteArray
    ) : CryptographyMLSException() {
        override val message
            get() = "conversationId=$conversationId"
    }

    class DuplicateMessage : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class BufferedFutureMessage : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class WrongEpoch : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class BufferedCommit : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class MessageEpochTooOld : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class SelfCommitIgnored : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class UnmergedPendingGroup : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class StaleProposal : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class StaleCommit : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class OrphanWelcome : CryptographyMLSException() {
        override val message
            get() = ""
    }

    class MessageRejected(
        private val reason: String
    ) : CryptographyMLSException() {
        override val message
            get() = reason
    }

    class Other(override val message: String) : CryptographyMLSException()
}
