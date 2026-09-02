/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation.createconversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.CreateGroupConversationResult
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isOperationDenied
import com.wire.kalium.util.DateTimeUtil

/**
 * Creates a conversation.
 * Can be used to create a group conversation or a channel.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [ConversationCreationResult].
 */
@Suppress("LongParameterList")
internal interface GroupConversationCreator {

    /**
     * @param name the name of the conversation
     * @param userIdList list of members
     * @param options settings that customise the conversation
     */
    suspend operator fun invoke(
        name: String,
        userIdList: List<UserId>,
        options: CreateConversationParam
    ): ConversationCreationResult

    suspend fun retryPendingMLSGroupCreation(conversationId: ConversationId): ConversationCreationResult

    suspend fun discardPendingMLSGroupCreation(conversationId: ConversationId): Boolean
}

/**
 * Implementation of [GroupConversationCreator].
 */
@Suppress("LongParameterList")
internal class GroupConversationCreatorImpl(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val pendingActionsRepository: PendingActionsRepository,
) : GroupConversationCreator {

    override suspend fun invoke(
        name: String,
        userIdList: List<UserId>,
        options: CreateConversationParam
    ): ConversationCreationResult {
        val clientId = syncManager.waitUntilLiveOrFailure().flatMap {
            currentClientIdProvider()
        }.fold(
            { return it.toCreationFailure() },
            { it }
        )

        return when (
            val result = conversationGroupRepository.createGroupConversationWithPendingResult(
                name,
                userIdList,
                options.copy(creatorClientId = clientId)
            )
        ) {
            is CreateGroupConversationResult.Failure -> result.cause.toCreationFailureWithTerminalCleanup(result.conversationId)
            is CreateGroupConversationResult.PendingMLSGroupCreation ->
                ConversationCreationResult.PendingMLSGroupCreation(result.conversationId, result.cause)
            is CreateGroupConversationResult.Success -> finishSuccessfulCreation(result.conversation)
        }
    }

    override suspend fun retryPendingMLSGroupCreation(conversationId: ConversationId): ConversationCreationResult =
        syncManager.waitUntilLiveOrFailure().flatMap {
            conversationRepository.getConversationById(conversationId)
        }.flatMap { conversation ->
            val groupState = (conversation.protocol as? Conversation.ProtocolInfo.MLSCapable)?.groupState
            if (groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED) {
                Either.Right(conversation)
            } else {
                transactionProvider.transaction("retryPendingMLSGroupCreation") { transactionContext ->
                    joinExistingMLSConversation(transactionContext, conversationId)
                }.flatMap {
                    conversationRepository.getConversationById(conversationId)
                }.flatMap { recoveredConversation ->
                    if (recoveredConversation.isMLSEstablished()) {
                        Either.Right(recoveredConversation)
                    } else {
                        Either.Left(MLSFailure.Other("MLS group is still pending after creation retry"))
                    }
                }
            }
        }.fold(
            { failure -> failure.toCreationFailureWithTerminalCleanup(conversationId) },
            { conversation ->
                val result = finishSuccessfulCreation(conversation)
                if (result is ConversationCreationResult.Success) {
                    pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversationId))
                }
                result
            }
        )

    override suspend fun discardPendingMLSGroupCreation(conversationId: ConversationId): Boolean {
        val wasDeleted = conversationRepository.setConversationDeletedLocally(conversationId, true).fold(
            { failure ->
                kaliumLogger.w("Failed to discard pending MLS conversation: $failure")
                false
            },
            { true }
        )
        pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversationId))
        return wasDeleted
    }

    private fun Conversation.isMLSEstablished(): Boolean {
        val mlsProtocol = protocol as? Conversation.ProtocolInfo.MLSCapable
        return mlsProtocol?.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
    }

    private suspend fun finishSuccessfulCreation(conversation: Conversation): ConversationCreationResult =
        Either.Right(conversation).onSuccess {
            refreshUsersWithoutMetadata()
        }.flatMap { conversation ->
            // TODO(qol): this can be done in one query, e.g. pass current time when inserting
            conversationRepository.updateConversationModifiedDate(conversation.id, DateTimeUtil.currentInstant())
                .map { conversation }
        }.fold({ failure ->
            failure.toCreationFailure()
        }, { createdConversation ->
            newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(createdConversation)
            ConversationCreationResult.Success(createdConversation)
        })

    private suspend fun CoreFailure.toCreationFailureWithTerminalCleanup(
        conversationId: ConversationId? = null,
    ): ConversationCreationResult {
        val result = toCreationFailure(conversationId)
        return if (result is ConversationCreationResult.BackendConflictFailure && conversationId != null) {
            if (discardPendingMLSGroupCreation(conversationId)) {
                ConversationCreationResult.BackendConflictFailure(result.domains)
            } else {
                result
            }
        } else {
            result
        }
    }

    private fun CoreFailure.toCreationFailure(conversationId: ConversationId? = null): ConversationCreationResult =
        when (val failure = normalizeFederatedBackendConflict()) {
            is NetworkFailure.NoNetworkConnection -> {
                ConversationCreationResult.SyncFailure
            }

            is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> {
                ConversationCreationResult.BackendConflictFailure(failure.domains, conversationId)
            }

            is NetworkFailure.ServerMiscommunication -> {
                val exception = failure.kaliumException
                if (exception is KaliumException.InvalidRequestError && exception.isOperationDenied()
                ) {
                    ConversationCreationResult.Forbidden
                } else {
                    ConversationCreationResult.UnknownFailure(failure)
                }
            }

            else -> {
                ConversationCreationResult.UnknownFailure(failure)
            }
        }
}
