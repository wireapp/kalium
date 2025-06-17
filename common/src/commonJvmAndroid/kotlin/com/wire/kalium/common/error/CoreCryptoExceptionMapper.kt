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
package com.wire.kalium.common.error

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException

@Suppress("CyclomaticComplexMethod")
actual fun mapMLSException(exception: Exception): MLSFailure {
    return if (exception is CoreCryptoException.Mls) {
        when (exception.exception) {
            is MlsException.WrongEpoch -> MLSFailure.WrongEpoch
            is MlsException.DuplicateMessage -> MLSFailure.DuplicateMessage
            is MlsException.BufferedFutureMessage -> MLSFailure.BufferedFutureMessage
            is MlsException.SelfCommitIgnored -> MLSFailure.SelfCommitIgnored
            is MlsException.UnmergedPendingGroup -> MLSFailure.UnmergedPendingGroup
            is MlsException.StaleProposal -> MLSFailure.StaleProposal
            is MlsException.StaleCommit -> MLSFailure.StaleCommit
            is MlsException.ConversationAlreadyExists -> MLSFailure.ConversationAlreadyExists
            is MlsException.MessageEpochTooOld -> MLSFailure.MessageEpochTooOld

            is MlsException.Other -> {
                val otherError = (exception.exception as MlsException.Other).message
                if (otherError.startsWith(COMMIT_FOR_MISSING_PROPOSAL)) {
                    MLSFailure.CommitForMissingProposal
                } else if (otherError.startsWith(CONVERSATION_NOT_FOUND)) {
                    MLSFailure.ConversationNotFound
                } else {
                    MLSFailure.Other
                }
            }

            is MlsException.OrphanWelcome -> MLSFailure.OrphanWelcome
            is MlsException.BufferedCommit -> MLSFailure.BufferedCommit
            is MlsException.MessageRejected -> mapMessageRejected((exception.exception as MlsException.MessageRejected).message)
        }
        // because there is a lack of multiplatform binding we need to catch generic exception
        // and map it to the appropriate failure to have proper tests
    } else if (exception.message != null && containsMessageRejected(exception.message as String)) {
        mapMessageRejected(exception.message as String)
    } else {
        MLSFailure.Generic(exception)
    }
}

private fun mapMessageRejected(message: String): MLSFailure.MessageRejected {
    val reason = message.replace("reason=", "")
    return when (reason) {
        "mls-stale-message" -> MLSFailure.MessageRejected.MlsStaleMessage
        "mls-client-mismatch" -> MLSFailure.MessageRejected.MlsClientMismatch
        "mls-commit-missing-references" -> MLSFailure.MessageRejected.MlsCommitMissingReferences
        else -> MLSFailure.MessageRejected.Other(reason = reason)
    }
}

private fun containsMessageRejected(message: String): Boolean =
    message.contains("mls-stale-message")
            || message.contains("mls-client-mismatch")
            || message.contains("mls-commit-missing-references")

private const val COMMIT_FOR_MISSING_PROPOSAL = "Incoming message is a commit for which we have not yet received all the proposals"
private const val CONVERSATION_NOT_FOUND = "Couldn't find conversation"
