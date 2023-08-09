/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap

internal interface MLSConnectionMigrator {
    suspend fun migrateConnectionToMLS(
        connection: Connection
    ): Either<CoreFailure, Unit>
}

internal class MLSConnectionMigratorImpl(
    private val getResolvedMLSOneOnOne: MLSOneOnOneConversationResolver,
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository
) : MLSConnectionMigrator {

    override suspend fun migrateConnectionToMLS(
        connection: Connection
    ): Either<CoreFailure, Unit> = getResolvedMLSOneOnOne(connection.qualifiedToId)
        .flatMap { mlsConversation ->
            if (connection.qualifiedConversationId == mlsConversation) {
                return@flatMap Either.Right(Unit)
            }

            messageRepository.moveMessagesToAnotherConversation(
                originalConversation = connection.qualifiedConversationId,
                targetConversation = mlsConversation
            ).flatMap {
                connectionRepository.updateConversationForConnection(
                    conversationId = mlsConversation,
                    userId = connection.qualifiedToId
                )
            }
        }
}
