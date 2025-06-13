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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsMissingGroupInfo
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext

/**
 * Send an external commit to join an MLS conversation for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
@Mockable
internal interface JoinExistingMLSConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, mlsPublicKeys: MLSPublicKeys? = null): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class JoinExistingMLSConversationUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val conversationApi: ConversationApi,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val selfUserId: UserId,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : JoinExistingMLSConversationUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend operator fun invoke(conversationId: ConversationId, mlsPublicKeys: MLSPublicKeys?): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("$TAG: Skip re-join existing MLS conversation, since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.getConversationById(conversationId).fold({
                Either.Left(StorageFailure.DataNotFound)
            }, { conversation ->
                withContext(dispatcher) {
                    joinOrEstablishMLSGroupAndRetry(conversation, mlsPublicKeys)
                }
            })
        }

    private suspend fun joinOrEstablishMLSGroupAndRetry(
        conversation: Conversation,
        mlsPublicKeys: MLSPublicKeys?
    ): Either<CoreFailure, Unit> =
        joinOrEstablishMLSGroup(conversation, mlsPublicKeys)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if ((failure.kaliumException as KaliumException.InvalidRequestError).isMlsStaleMessage()) {
                        kaliumLogger.logStructuredJson(
                            level = KaliumLogLevel.WARN,
                            leadingMessage = "Join-Establish MLS Group Stale",
                            jsonStringKeyValues = mapOf(
                                "conversationId" to conversation.id.toLogString(),
                                "protocol" to CreateConversationParam.Protocol.MLS.name,
                                "protocolInfo" to conversation.protocol.toLogMap(),
                                "errorInfo" to "$failure"
                            )
                        )
                        // Re-fetch current epoch and try again
                        if (conversation.type == Conversation.Type.OneOnOne) {
                            conversationRepository.getConversationMembers(conversation.id).flatMap {
                                conversationRepository.fetchMlsOneToOneConversation(it.first()).map {
                                    it.mlsPublicKeys
                                }
                            }
                        } else {
                            conversationRepository.fetchConversation(conversation.id)
                        }.flatMap {
                            conversationRepository.getConversationById(conversation.id).flatMap { conversation ->
                                joinOrEstablishMLSGroup(conversation, null)
                            }
                        }
                    } else if ((failure.kaliumException as KaliumException.InvalidRequestError).isMlsMissingGroupInfo()) {
                        kaliumLogger.w("$TAG: conversation has no group info, ignoring...")
                        Either.Right(Unit)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

    @Suppress("LongMethod")
    private suspend fun joinOrEstablishMLSGroup(conversation: Conversation, publicKeys: MLSPublicKeys?): Either<CoreFailure, Unit> {
        val protocol = conversation.protocol
        val type = conversation.type
        return when {
            protocol !is Conversation.ProtocolInfo.MLSCapable -> Either.Right(Unit)

            protocol.epoch != 0UL -> {
                // TODO(refactor): don't use conversationAPI directly
                //                 we could use mlsConversationRepository to solve this
                kaliumLogger.d("$TAG: Joining group by external commit ${conversation.id.toLogString()}")
                wrapApiRequest {
                    conversationApi.fetchGroupInfo(conversation.id.toApi())
                }.flatMap { groupInfo ->
                    mlsConversationRepository.joinGroupByExternalCommit(
                        protocol.groupId,
                        groupInfo
                    ).flatMapLeft { failure ->
                        if (MLSMessageFailureHandler.handleFailure(failure) is MLSMessageFailureResolution.Ignore) {
                            kaliumLogger.logStructuredJson(
                                level = KaliumLogLevel.WARN,
                                leadingMessage = "Join Group external commit Ignored",
                                jsonStringKeyValues = mapOf(
                                    "conversationId" to conversation.id.toLogString(),
                                    "conversationType" to type,
                                    "protocol" to CreateConversationParam.Protocol.MLS.name,
                                    "protocolInfo" to conversation.protocol.toLogMap(),
                                    "errorInfo" to "$failure"
                                )
                            )
                            Either.Right(Unit)
                        } else {
                            kaliumLogger.logStructuredJson(
                                level = KaliumLogLevel.ERROR,
                                leadingMessage = "Join Group external commit Failure",
                                jsonStringKeyValues = mapOf(
                                    "conversationId" to conversation.id.toLogString(),
                                    "conversationType" to type,
                                    "protocol" to CreateConversationParam.Protocol.MLS.name,
                                    "protocolInfo" to conversation.protocol.toLogMap(),
                                    "errorInfo" to "$failure"
                                )
                            )
                            Either.Left(failure)
                        }
                    }
                }.onSuccess {
                    kaliumLogger.logStructuredJson(
                        level = KaliumLogLevel.INFO,
                        leadingMessage = "Join Group external commit Success",
                        jsonStringKeyValues = mapOf(
                            "conversationId" to conversation.id.toLogString(),
                            "conversationType" to type,
                            "protocol" to CreateConversationParam.Protocol.MLS.name,
                            "protocolInfo" to conversation.protocol.toLogMap(),
                        )
                    )
                }
            }

            type == Conversation.Type.Self -> {
                kaliumLogger.d("$TAG: Establish Self MLS Conversation ${conversation.id.toLogString()}")
                mlsConversationRepository.establishMLSGroup(
                    protocol.groupId,
                    listOf(selfUserId)
                )
                    .onSuccess {
                        kaliumLogger.logStructuredJson(
                            level = KaliumLogLevel.INFO,
                            leadingMessage = "Establish Group",
                            jsonStringKeyValues = mapOf(
                                "conversationId" to conversation.id.toLogString(),
                                "conversationType" to type,
                                "protocol" to CreateConversationParam.Protocol.MLS.name,
                                "protocolInfo" to conversation.protocol.toLogMap(),
                            )
                        )
                    }.map { Unit }
            }

            type == Conversation.Type.OneOnOne -> {
                kaliumLogger.d("$TAG: Establish 1on1 MLS Conversation ${conversation.id.toLogString()}")
                conversationRepository.getConversationMembers(conversation.id).flatMap { members ->
                    mlsConversationRepository.establishMLSGroup(
                        protocol.groupId,
                        members,
                        publicKeys
                    )
                }.onSuccess {
                    kaliumLogger.logStructuredJson(
                        level = KaliumLogLevel.INFO,
                        leadingMessage = "Establish Group",
                        jsonStringKeyValues = mapOf(
                            "conversationId" to conversation.id.toLogString(),
                            "conversationType" to type,
                            "protocol" to CreateConversationParam.Protocol.MLS.name,
                            "protocolInfo" to conversation.protocol.toLogMap(),
                        )
                    )
                }.map { Unit }
            }

            else -> Either.Right(Unit)
        }
    }

    companion object {
        private const val TAG = "[JoinExistingMLSConversationUseCase]"
    }
}
