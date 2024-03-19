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
package com.wire.kalium.logic

import com.wire.crypto.CoreCryptoException
import uniffi.core_crypto.CryptoError

actual fun mapMLSException(exception: Exception): MLSFailure =
        if (exception is CoreCryptoException.CryptoException) {
            when (exception.error) {
                is CryptoError.WrongEpoch -> MLSFailure.WrongEpoch
                is CryptoError.DuplicateMessage -> MLSFailure.DuplicateMessage
                is CryptoError.BufferedFutureMessage -> MLSFailure.BufferedFutureMessage
                is CryptoError.SelfCommitIgnored -> MLSFailure.SelfCommitIgnored
                is CryptoError.UnmergedPendingGroup -> MLSFailure.UnmergedPendingGroup
                is CryptoError.StaleProposal -> MLSFailure.StaleProposal
                is CryptoError.StaleCommit -> MLSFailure.StaleCommit
                is CryptoError.ConversationAlreadyExists -> MLSFailure.ConversationAlreadyExists
                is CryptoError.MessageEpochTooOld -> MLSFailure.MessageEpochTooOld
                else -> MLSFailure.Generic(exception)
            }
        } else {
            MLSFailure.Generic(exception)
        }
