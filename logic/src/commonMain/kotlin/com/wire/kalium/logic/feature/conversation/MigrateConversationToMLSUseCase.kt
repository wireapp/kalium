/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator

/**
 * This use case will migrate a given conversation to use the MLS protocol
 */
public interface MigrateConversationToMLSUseCase {
    /**
     * @param conversationId the id of the conversation
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    public suspend operator fun invoke(conversationId: ConversationId): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val cause: CoreFailure) : Result
    }
}

internal class MigrateConversationToMLSUseCaseImpl(
    val mlsMigrator: MLSMigrator,
    val conversationRepository: ConversationRepository,
    val coreCryptoTransactionProvider: CryptoTransactionProvider
) : MigrateConversationToMLSUseCase {
    override suspend fun invoke(conversationId: ConversationId): MigrateConversationToMLSUseCase.Result {
        return conversationRepository.getConversationProtocolInfo(conversationId)
            .flatMap { protocolInfo ->
                when (protocolInfo) {
                    is Conversation.ProtocolInfo.MLS -> Either.Right(Unit)
                    is Conversation.ProtocolInfo.Mixed -> {
                        coreCryptoTransactionProvider.transaction {
                            mlsMigrator.finalise(it, conversationId)
                        }
                    }
                    Conversation.ProtocolInfo.Proteus -> {
                        coreCryptoTransactionProvider.transaction { context ->
                            mlsMigrator.migrate(context, conversationId).flatMap {
                                mlsMigrator.finalise(context, conversationId)
                            }
                        }
                    }
                }
            }.fold({
                MigrateConversationToMLSUseCase.Result.Failure(it)
            }, {
                MigrateConversationToMLSUseCase.Result.Success
            })
    }
}
