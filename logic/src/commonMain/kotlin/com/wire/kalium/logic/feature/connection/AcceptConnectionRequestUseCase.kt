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
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.DateTimeUtil

/**
 * Use Case that allows a user accept a connection request to connect with another User
 */
interface AcceptConnectionRequestUseCase {
    /**
     * Use case [AcceptConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [AcceptConnectionRequestUseCaseResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult
}

internal class AcceptConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val oneOnOneResolver: OneOnOneResolver,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
) : AcceptConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.ACCEPTED)
            .flatMap { connection ->
                conversationRepository.fetchConversation(connection.qualifiedConversationId)
                    .flatMap {
                        conversationRepository.updateConversationModifiedDate(
                            connection.qualifiedConversationId,
                            DateTimeUtil.currentInstant()
                        )
                    }.flatMap {
                        oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                            userId = connection.qualifiedToId,
                            invalidateCurrentKnownProtocols = true
                        ).map { }
                    }.flatMap {
                        newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                            connection.qualifiedConversationId
                        )
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

sealed class AcceptConnectionRequestUseCaseResult {
    data object Success : AcceptConnectionRequestUseCaseResult()
    data class Failure(val coreFailure: CoreFailure) : AcceptConnectionRequestUseCaseResult()
}
