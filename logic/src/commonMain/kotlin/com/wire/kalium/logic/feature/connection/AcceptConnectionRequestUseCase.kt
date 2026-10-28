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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.util.DateTimeUtil

/**
 * Use Case that allows a user accept a connection request to connect with another User
 */
public interface AcceptConnectionRequestUseCase {
    /**
     * Use case [AcceptConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [AcceptConnectionRequestUseCaseResult] indicating the operation result
     */
    public suspend operator fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult
}

@Suppress("LongParameterList")
internal class AcceptConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val oneOnOneResolver: OneOnOneResolver,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val fetchConversation: FetchConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val pendingActionsRepository: PendingActionsRepository
) : AcceptConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult {
        return transactionProvider.transaction("AcceptConnection") { transactionContext ->
            connectionRepository.updateConnectionStatus(transactionContext, userId, ConnectionState.ACCEPTED)
                .flatMap { connection ->
                    fetchConversation(transactionContext, connection.qualifiedConversationId)
                        .flatMap {
                            conversationRepository.updateConversationModifiedDate(
                                connection.qualifiedConversationId,
                                DateTimeUtil.currentInstant()
                            )
                        }.flatMap {
                            oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                                transactionContext = transactionContext,
                                userId = connection.qualifiedToId,
                                invalidateCurrentKnownProtocols = true
                            ).map { }.flatMapLeft { failure ->
                                // The backend already accepted the connection, so this must not fail the accept.
                                // The other user can still establish the 1:1, otherwise we retry on next foreground.
                                kaliumLogger.w(
                                    "Accepted the connection request from ${userId.toLogString()} but resolving " +
                                        "the one-on-one failed: $failure, retrying on next foreground"
                                )
                                pendingActionsRepository.enqueuePendingOneOnOneResolution(connection.qualifiedToId)
                                Either.Right(Unit)
                            }
                        }.flatMap {
                            newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                                connection.qualifiedConversationId
                            )
                        }
                }
        }
            .fold({
                kaliumLogger.e("An error occurred when accepting the connection request from ${userId.toLogString()}: $it")
                AcceptConnectionRequestUseCaseResult.Failure(it)
            }, {
                AcceptConnectionRequestUseCaseResult.Success
            })
    }
}

public sealed class AcceptConnectionRequestUseCaseResult {
    public data object Success : AcceptConnectionRequestUseCaseResult()
    public data class Failure(val coreFailure: CoreFailure) : AcceptConnectionRequestUseCaseResult()
}
