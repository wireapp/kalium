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
package com.wire.kalium.cryptography.utils

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.kalium.cryptography.exceptions.CryptographyMLSException

@Suppress("CyclomaticComplexMethod")
actual fun mapMLSException(exception: Throwable): CryptographyMLSException {
    return if (exception is CoreCryptoException.Mls) {
        when (exception.exception) {
            is MlsException.WrongEpoch -> CryptographyMLSException.WrongEpoch()
            is MlsException.DuplicateMessage -> CryptographyMLSException.DuplicateMessage()
            is MlsException.BufferedFutureMessage -> CryptographyMLSException.BufferedFutureMessage()
            is MlsException.SelfCommitIgnored -> CryptographyMLSException.SelfCommitIgnored()
            is MlsException.UnmergedPendingGroup -> CryptographyMLSException.UnmergedPendingGroup()
            is MlsException.StaleProposal -> CryptographyMLSException.StaleProposal()
            is MlsException.StaleCommit -> CryptographyMLSException.StaleCommit()
            is MlsException.ConversationAlreadyExists -> CryptographyMLSException.ConversationAlreadyExists(
                conversationId = (exception.exception as MlsException.ConversationAlreadyExists).conversationId
            )
            is MlsException.MessageEpochTooOld -> CryptographyMLSException.MessageEpochTooOld()

            is MlsException.Other -> CryptographyMLSException.Other(
                message = (exception.exception as MlsException.Other).message,
            )

            is MlsException.OrphanWelcome -> CryptographyMLSException.OrphanWelcome()
            is MlsException.BufferedCommit -> CryptographyMLSException.BufferedCommit()
            is MlsException.MessageRejected -> CryptographyMLSException.MessageRejected(
                reason = (exception.exception as MlsException.MessageRejected).message,
            )
        }
    } else {
       CryptographyMLSException.Other(exception.message ?: "Unknown error")
    }
}
