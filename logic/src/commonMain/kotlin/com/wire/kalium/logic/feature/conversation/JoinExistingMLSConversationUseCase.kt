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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsMissingGroupInfo
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
interface JoinExistingMLSConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class JoinExistingMLSConversationUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val conversationApi: ConversationApi,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : JoinExistingMLSConversationUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("Skip re-join existing MLS conversation(s), since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.baseInfoById(conversationId).fold({
                Either.Left(StorageFailure.DataNotFound)
            }, { conversation ->
                withContext(dispatcher) {
                    joinOrEstablishMLSGroupAndRetry(conversation)
                }
            })
        }

    private suspend fun joinOrEstablishMLSGroupAndRetry(
        conversation: Conversation
    ): Either<CoreFailure, Unit> =
        joinOrEstablishMLSGroup(conversation)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if (failure.kaliumException.isMlsStaleMessage()) {
                        kaliumLogger.w("Epoch out of date for conversation ${conversation.id}, re-fetching and re-trying")
                        // Re-fetch current epoch and try again
                        conversationRepository.fetchConversation(conversation.id).flatMap {
                            conversationRepository.baseInfoById(conversation.id).flatMap { conversation ->
                                joinOrEstablishMLSGroup(conversation)
                            }
                        }
                    } else if (failure.kaliumException.isMlsMissingGroupInfo()) {
                        kaliumLogger.w("conversation has no group info, ignoring...")
                        Either.Right(Unit)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

    private suspend fun joinOrEstablishMLSGroup(conversation: Conversation): Either<CoreFailure, Unit> {
        return if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
            if (conversation.protocol.epoch == 0UL) {
                if (conversation.type == Conversation.Type.SELF) {
                    kaliumLogger.i("Establish group for ${conversation.type}")
                    mlsConversationRepository.establishMLSGroup(
                        conversation.protocol.groupId,
                        emptyList()
                    )
                } else {
                    Either.Right(Unit)
                }
            } else {
                wrapApiRequest {
                    conversationApi.fetchGroupInfo(conversation.id.toApi())
                }.flatMap { groupInfo ->
                    mlsConversationRepository.joinGroupByExternalCommit(
                        conversation.protocol.groupId,
                        groupInfo
                    ).flatMapLeft {
                        if (MLSMessageFailureHandler.handleFailure(it) is MLSMessageFailureResolution.Ignore) {
                            Either.Right(null)
                        } else {
                            Either.Left(it)
                        }
                    }.map { messageBundles ->
                        // Process any buffered message which arrived before the pending group was merged. We only
                        // expect to receive proposals which the backend re-creates upon external commits.
                        messageBundles?.forEach { bundle ->
                            mlsMessageUnpacker.unpackMlsBundle(bundle, conversation.id, Clock.System.now())
                        } ?: Unit
                    }
                }
            }
        } else {
            Either.Right(Unit)
        }
    }
}
