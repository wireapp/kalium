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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import io.mockative.Mockable

/**
 * Attempts to find an existing MLS-capable one-on-one conversation,
 * or creates a new one if none is found.
 * In case the conversation already exists, but it's not established yet
 * (see [GroupState.ESTABLISHED]), it will attempt to join it, returning failure if it fails.
 */
@Mockable
internal interface MLSOneOnOneConversationResolver {
    /**
     * Attempts to find an existing MLS-capable one-on-one conversation,
     * or creates a new one if none is found.
     * In case the conversation already exists, but it's not established yet
     * (see [GroupState.ESTABLISHED]), it will attempt to join it, returning failure if it fails.
     * @param userId The user ID of the other participant.
     */
    suspend operator fun invoke(transactionContext: CryptoTransactionContext, userId: UserId): Either<CoreFailure, ConversationId>
}

internal class MLSOneOnOneConversationResolverImpl(
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
    private val fetchMLSOneToOneConversation: FetchMLSOneToOneConversationUseCase
) : MLSOneOnOneConversationResolver {

    override suspend fun invoke(transactionContext: CryptoTransactionContext, userId: UserId): Either<CoreFailure, ConversationId> =
        conversationRepository.getConversationsByUserId(userId).flatMap { conversations ->
            // Look for an existing MLS-capable conversation one-on-one
            val initializedMLSOneOnOne = conversations.firstOrNull {
                val isOneOnOne = it.type == Conversation.Type.OneOnOne
                val protocol = it.protocol
                val isMLSInitialized = protocol is Conversation.ProtocolInfo.MLSCapable &&
                        protocol.groupState == GroupState.ESTABLISHED
                isOneOnOne && isMLSInitialized
            }

            if (initializedMLSOneOnOne != null) {
                kaliumLogger.d("Already established mls group for one-on-one with ${userId.toLogString()}, skipping.")
                Either.Right(initializedMLSOneOnOne.id)
            } else {
                kaliumLogger.d("Establishing mls group for one-on-one with ${userId.toLogString()}")
                fetchMLSOneToOneConversation(transactionContext, userId).flatMap { conversation ->
                    joinExistingMLSConversationUseCase(
                        transactionContext,
                        conversation.id,
                        conversation.mlsPublicKeys
                    ).map { conversation.id }
                }
            }
        }
}
