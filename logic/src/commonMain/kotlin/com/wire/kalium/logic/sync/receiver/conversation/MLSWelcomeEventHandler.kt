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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.processMLSWelcome
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.sync.receiving.isConversationUnavailable
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlinx.coroutines.flow.first

internal interface MLSWelcomeEventHandler {
    suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "LongMethod")
internal class MLSWelcomeEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val pendingActionsRepository: PendingActionsRepository,
    /** Starts resolving queued 1:1s once sync is live, outside this batch's transaction. */
    private val resolvePendingOneOnOnes: () -> Unit,
    private val joinPendingMLSGroups: () -> Unit,
    private val fetchConversationIfUnknown: FetchConversationIfUnknownUseCase,
    private val keyPackageManager: KeyPackageManager,
) : MLSWelcomeEventHandler {
    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.MLSWelcome
    ): Either<CoreFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        val mlsContext = transactionContext.mls
        if (mlsContext == null) {
            kaliumLogger.w("$TAG: mls is disabled to handle welcome message")
            return Unit.right()
        }

        var welcomeOutcome = OUTCOME_PROCESSED_DIRECTLY

        return fetchConversationIfUnknown(transactionContext, event.conversationId)
            .flatMap {
                kaliumLogger.d("$TAG: Processing MLS welcome message")
                processMLSWelcome(mlsContext, event.message)
            }
            .flatMap { groupId ->
                kaliumLogger.d("$TAG: Marking conversation as established ${groupId.obfuscateId()}")
                markConversationAsEstablished(GroupID(groupId))
            }.flatMap {
                queueResolutionIfOneOnOne(event.conversationId)
            }
            .flatMapLeft { failure ->
                when {
                    failure.isConversationUnavailable() -> {
                        welcomeOutcome = OUTCOME_SKIPPED_CONVERSATION_UNAVAILABLE
                        kaliumLogger.w(
                            "$TAG: Discarding welcome because the conversation is gone or no longer accessible " +
                                    "[conversationId=${event.conversationId.toLogString()}]"
                        )
                        Either.Right(Unit)
                    }

                    failure is MLSFailure.ConversationAlreadyExists -> {
                        kaliumLogger.w("$TAG: Discarding welcome since the conversation already exists")
                        Either.Right(Unit)
                    }

                    failure is MLSFailure.OrphanWelcome -> {
                        if (isAlreadyEstablishedLocally(mlsContext, event.conversationId)) {
                            welcomeOutcome = OUTCOME_SKIPPED_ALREADY_ESTABLISHED
                            kaliumLogger.w(
                                "$TAG: OrphanWelcome for already established local MLS group; " +
                                        "treating as duplicate welcome and skipping external commit rejoin " +
                                        "[conversationId=${event.conversationId.toLogString()}]"
                            )
                            Unit.right()
                        } else {
                            kaliumLogger.w(
                                "$TAG: OrphanWelcome, discarding welcome and queueing exact-ID group recovery " +
                                        "[conversationId=${event.conversationId.toLogString()}]"
                            )
                            pendingActionsRepository.requestMLSGroupJoin(event.conversationId)
                            joinPendingMLSGroups()
                            welcomeOutcome = OUTCOME_QUEUED_EXTERNAL_COMMIT_REJOIN
                            Unit.right()
                        }
                    }

                    else -> {
                        welcomeOutcome = OUTCOME_FAILED
                        Either.Left(failure)
                    }
                }
            }
            .onSuccess {
                if (welcomeOutcome == OUTCOME_SKIPPED_CONVERSATION_UNAVAILABLE) {
                    eventLogger.logComplete(
                        EventLoggingStatus.SKIPPED,
                        arrayOf("welcomeOutcome" to welcomeOutcome)
                    )
                } else {
                    keyPackageManager.scheduleRefill()
                    eventLogger.logSuccess(
                        "info" to "Established mls conversation from welcome message",
                        "welcomeOutcome" to welcomeOutcome
                    )
                }
            }
            .onFailure { eventLogger.logFailure(it, "welcomeOutcome" to welcomeOutcome) }
    }

    private suspend fun isAlreadyEstablishedLocally(
        mlsContext: MlsCoreCryptoContext,
        conversationId: ConversationId
    ): Boolean = conversationRepository.getConversationProtocolInfo(conversationId)
        .map { it as? Conversation.ProtocolInfo.MLSCapable }
        .flatMap { mlsProtocol ->
            when {
                mlsProtocol == null -> Either.Right(false)
                mlsProtocol.groupState != Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> Either.Right(false)
                else -> wrapMLSRequest { mlsContext.conversationExists(mlsProtocol.groupId.toCrypto()) }
            }
        }
        .fold({ false }, { it })

    private suspend fun markConversationAsEstablished(groupID: GroupID): Either<CoreFailure, Unit> =
        conversationRepository.updateConversationGroupState(groupID, Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)

    // Resolving the 1:1 inline could fail this whole batch, and every event queued after it, on a
    // network or protocol problem. The queue entry is durable, so the resolution survives a failed batch.
    private suspend fun queueResolutionIfOneOnOne(conversationId: ConversationId): Either<CoreFailure, Unit> =
        conversationRepository.observeConversationDetailsById(conversationId)
            .first()
            .map {
                if (it is ConversationDetails.OneOne) {
                    kaliumLogger.d("$TAG: Queueing one-on-one resolution ${conversationId.toLogString()}")
                    pendingActionsRepository.enqueuePendingOneOnOneResolution(it.otherUser.id)
                    resolvePendingOneOnOnes()
                }
            }

    companion object {
        private const val TAG = "[MLSWelcomeEventHandler]"

        // Values for the structured "welcomeOutcome" field on the event log. Kept short and
        // stable so they can be used as filters/aggregations in log-analysis tooling.
        private const val OUTCOME_PROCESSED_DIRECTLY = "processed-directly"
        private const val OUTCOME_QUEUED_EXTERNAL_COMMIT_REJOIN = "queued-exact-id-recovery"
        private const val OUTCOME_SKIPPED_ALREADY_ESTABLISHED = "skipped-already-established"
        private const val OUTCOME_SKIPPED_CONVERSATION_UNAVAILABLE = "skipped-conversation-unavailable"
        private const val OUTCOME_FAILED = "failed"
    }

}
