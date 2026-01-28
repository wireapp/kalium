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
actual fun commonizeMLSException(exception: Exception): CommonizedMLSException {
    return if (exception is CoreCryptoException.Mls) {
        when (val mlsError = exception.mlsError) {
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
                val otherError = mlsError.msg
                if (otherError.startsWith(COMMIT_FOR_MISSING_PROPOSAL)) {
                    MLSFailure.CommitForMissingProposal
                } else if (otherError.startsWith(CONVERSATION_NOT_FOUND)) {
                    MLSFailure.ConversationNotFound
                } else if (otherError.startsWith(INVALID_GROUP_ID)) {
                    MLSFailure.InvalidGroupId
                } else {
                    MLSFailure.Other(mlsError.msg)
                }
            }

            is MlsException.OrphanWelcome -> MLSFailure.OrphanWelcome
            is MlsException.BufferedCommit -> MLSFailure.BufferedCommit
            is MlsException.MessageRejected -> mapMessageRejected(mlsError)
        }
    } else {
        MLSFailure.Generic(exception)
    }.run { CommonizedMLSException(this, exception) }
}

private fun mapMessageRejected(mlsError: MlsException.MessageRejected): MLSFailure {
    return MLSTransportFailureSerialization.parseString(mlsError.reason)
}

private const val COMMIT_FOR_MISSING_PROPOSAL = "Incoming message is a commit for which we have not yet received all the proposals"
private const val CONVERSATION_NOT_FOUND = "Couldn't find conversation"
private const val INVALID_GROUP_ID = "Message group ID differs from the group's group ID"
