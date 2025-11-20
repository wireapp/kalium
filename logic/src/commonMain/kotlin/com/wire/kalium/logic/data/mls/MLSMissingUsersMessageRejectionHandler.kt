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
package com.wire.kalium.logic.data.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID

interface MLSMissingUsersMessageRejectionHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        groupId: GroupID,
        mlsFailure: NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync
    ): Either<CoreFailure, Unit>
}

internal class MLSMissingUsersMessageRejectionHandlerImpl(
    private val mlsMemberAdder: MLSMemberAdder,
    private val protocolGetter: ConversationProtocolGetter,
    logger: KaliumLogger
) : MLSMissingUsersMessageRejectionHandler {

    private val logger = logger.withTextTag("MLSMissingUsersMessageRejectionHandler")

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        groupId: GroupID,
        mlsFailure: NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync
    ): Either<CoreFailure, Unit> = transactionContext.wrapInMLSContext { mlsContext ->
        logger.i("Attempting to handle Missing Users from MLS conversation ${conversationId.toLogString()}")
        val missingUsers = mlsFailure.missingUsers
        return@wrapInMLSContext if (missingUsers.isEmpty()) {
            Either.Right(Unit).also {
                logger.d("No users to add to the MLS group.")
            }
        } else protocolGetter.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                Either.Right(CipherSuite.fromTag(protocolInfo.cipherSuite.tag))
            } else {
                logger.w("Conversation does not support MLS!")
                Either.Left(MLSFailure.ConversationDoesNotSupportMLS)
            }
        }.flatMap { cipherSuite ->
            mlsMemberAdder.addMemberToMLSGroup(
                mlsContext = mlsContext,
                groupID = groupId,
                userIdList = missingUsers,
                cipherSuite = cipherSuite
            )
        }
    }
}
