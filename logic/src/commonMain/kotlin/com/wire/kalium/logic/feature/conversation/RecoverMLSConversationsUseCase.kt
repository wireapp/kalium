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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import io.mockative.Mockable

sealed class RecoverMLSConversationsResult {
    data object Success : RecoverMLSConversationsResult()
    data class Failure(val failure: CoreFailure) : RecoverMLSConversationsResult()
}

/**
 * Iterate over all MLS Established conversations after 404 sync error and
 * check for out of sync epochs, if out of sync then it tries to re-join.
 */
@Mockable
internal interface RecoverMLSConversationsUseCase {
    suspend operator fun invoke(transactionContext: CryptoTransactionContext): RecoverMLSConversationsResult
}

@Suppress("LongParameterList")
internal class RecoverMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
) : RecoverMLSConversationsUseCase {
    override suspend operator fun invoke(transactionContext: CryptoTransactionContext): RecoverMLSConversationsResult =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("Skip attempting to recover established MLS conversation(s), since MLS is not supported.")
            RecoverMLSConversationsResult.Success
        } else {
            conversationRepository.getConversationsByGroupState(GroupState.ESTABLISHED)
                .flatMap { groups ->
                    groups.map { recoverMLSGroup(transactionContext, it) }
                        .foldToEitherWhileRight(Unit) { value, _ -> value }
                }.fold(
                    { RecoverMLSConversationsResult.Failure(it) },
                    { RecoverMLSConversationsResult.Success }
                )
        }

    private suspend fun recoverMLSGroup(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation
    ): Either<CoreFailure, Unit> {
        val protocol = conversation.protocol
        return if (protocol is Conversation.ProtocolInfo.MLS) {
            transactionContext.wrapInMLSContext { mlsContext ->
                mlsConversationRepository.isGroupOutOfSync(mlsContext, protocol.groupId, protocol.epoch)
            }
                .fold({ checkEpochFailure ->
                    Either.Left(checkEpochFailure)
                }, { isGroupOutOfSync ->
                    if (isGroupOutOfSync) {
                        joinExistingMLSConversationUseCase(transactionContext, conversation.id).onFailure { joinFailure ->
                            Either.Left(joinFailure)
                        }
                    } else {
                        Either.Right(Unit)
                    }
                })
        } else {
            Either.Right(Unit)
        }
    }
}
