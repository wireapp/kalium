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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.util.ConversationPersistenceApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal interface StaleEpochVerifier {
    suspend fun verifyEpoch(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        subConversationId: SubconversationId? = null,
        timestamp: Instant? = null
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class StaleEpochVerifierImpl(
    private val systemMessageInserter: SystemMessageInserter,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val conversationRepository: ConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
) : StaleEpochVerifier {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }
    override suspend fun verifyEpoch(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        subConversationId: SubconversationId?,
        timestamp: Instant?
    ): Either<CoreFailure, Unit> {
        return if (subConversationId != null) {
            verifySubConversationEpoch(transactionContext, conversationId, subConversationId, timestamp)
        } else {
            verifyConversationEpoch(transactionContext, conversationId, timestamp)
        }
    }

    private suspend fun verifyConversationEpoch(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        timestamp: Instant?
    ): Either<CoreFailure, Unit> {
        logger.i("Verifying stale epoch for conversation ${conversationId.toLogString()}")
        return getUpdatedConversationProtocolInfo(transactionContext, conversationId).flatMap { remoteMlsInfo ->
            compareEpochsAndLog(
                transactionContext = transactionContext,
                conversationId = conversationId,
                subConversationId = null,
                groupId = remoteMlsInfo.groupId,
                remoteEpoch = remoteMlsInfo.epoch,
                timestamp = timestamp
            ).map { it.isLocalEpochStale }
        }.flatMap { hasMissedCommits ->
            if (hasMissedCommits) {
                logger.w("Epoch stale due to missing commits, re-joining")
                joinExistingMLSConversation(transactionContext, conversationId).flatMap {
                    systemMessageInserter.insertLostCommitSystemMessage(
                        conversationId,
                        Clock.System.now()
                    )
                }
            } else {
                logger.i("Epoch stale due to unprocessed events")
                Either.Right(Unit)
            }
        }
    }

    private suspend fun verifySubConversationEpoch(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        timestamp: Instant?
    ): Either<CoreFailure, Unit> {
        logger.i("Verifying stale epoch for subconversation ${subConversationId.toLogString()}")
        return subconversationRepository.fetchRemoteSubConversationDetails(conversationId, subConversationId)
            .flatMap { subConversationDetails ->
                compareEpochsAndLog(
                    transactionContext = transactionContext,
                    conversationId = conversationId,
                    subConversationId = subConversationId,
                    groupId = subConversationDetails.groupId,
                    remoteEpoch = subConversationDetails.epoch,
                    timestamp = timestamp
                )
                    .map { it.isLocalEpochStale }
                    .flatMap { hasMissedCommits ->
                        if (hasMissedCommits) {
                            logger.w("Epoch stale due to missing commits, joining by external commit")
                            subconversationRepository.fetchRemoteSubConversationGroupInfo(conversationId, subConversationId)
                                .flatMap { groupInfo ->
                                    mlsConversationRepository.joinGroupByExternalCommit(
                                        transactionContext.mls!!,
                                        subConversationDetails.groupId,
                                        groupInfo
                                    )
                                }
                        } else {
                            logger.i("Epoch stale due to unprocessed events")
                            Either.Right(Unit)
                        }
                    }
            }
    }

    private suspend fun compareEpochsAndLog(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        subConversationId: SubconversationId?,
        groupId: GroupID,
        remoteEpoch: ULong,
        timestamp: Instant?
    ): Either<CoreFailure, EpochComparison> = transactionContext.wrapInMLSContext {
        mlsConversationRepository.getLocalGroupEpoch(it, groupId)
    }.map { localEpoch ->
        EpochComparison(localEpoch, remoteEpoch).also { comparison ->
            logger.logStructuredJson(
                level = if (comparison.isLocalEpochStale) KaliumLogLevel.WARN else KaliumLogLevel.INFO,
                leadingMessage = "MLS epoch comparison",
                jsonStringKeyValues = mapOf(
                    "conversationId" to conversationId.toLogString(),
                    "subConversationId" to subConversationId?.toLogString(),
                    "groupId" to groupId.toLogString(),
                    "localEpoch" to comparison.localEpoch.toString(),
                    "remoteEpoch" to comparison.remoteEpoch.toString(),
                    "epochGap" to comparison.epochGap.toString(),
                    "epochState" to comparison.state.name,
                    "trigger" to if (timestamp == null) "SEND_FAILURE" else "RECEIVE_FAILURE",
                    "messageTimestamp" to timestamp,
                    "recoveryAction" to when {
                        !comparison.isLocalEpochStale -> "NONE"
                        subConversationId == null -> "REJOIN"
                        else -> "EXTERNAL_COMMIT"
                    }
                )
            )
        }
    }

    @OptIn(ConversationPersistenceApi::class)
    private suspend fun getUpdatedConversationProtocolInfo(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, RemoteMLSConversationInfo> {
        return fetchConversationUseCase(
            transactionContext,
            conversationId,
            ConversationSyncReason.Other
        ).flatMap {
            conversationRepository.getConversationProtocolInfo(conversationId)
        }.flatMap { protocolInfo ->
            protocolInfo.toRemoteMLSConversationInfo()
        }
    }

    private fun Conversation.ProtocolInfo.toRemoteMLSConversationInfo(): Either<CoreFailure, RemoteMLSConversationInfo> {
        return when (this) {
            !is Conversation.ProtocolInfo.MLS ->
                Either.Left(MLSFailure.ConversationDoesNotSupportMLS)

            else ->
                Either.Right(RemoteMLSConversationInfo(groupId, epoch))
        }
    }

    private data class RemoteMLSConversationInfo(
        val groupId: GroupID,
        val epoch: ULong
    )

    private data class EpochComparison(
        val localEpoch: ULong,
        val remoteEpoch: ULong
    ) {
        val isLocalEpochStale: Boolean = localEpoch < remoteEpoch
        val epochGap: ULong = if (isLocalEpochStale) remoteEpoch - localEpoch else localEpoch - remoteEpoch
        val state: State = when {
            isLocalEpochStale -> State.LOCAL_BEHIND_REMOTE
            localEpoch > remoteEpoch -> State.LOCAL_AHEAD_OF_REMOTE
            else -> State.IN_SYNC
        }

        enum class State {
            LOCAL_BEHIND_REMOTE,
            IN_SYNC,
            LOCAL_AHEAD_OF_REMOTE
        }
    }
}
