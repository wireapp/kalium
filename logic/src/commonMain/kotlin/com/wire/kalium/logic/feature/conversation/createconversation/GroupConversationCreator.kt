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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.CreateGroupConversationFailure
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isOperationDenied
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mockable

/**
 * Creates a conversation.
 * Can be used to create a group conversation or a channel.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [ConversationCreationResult].
 */
@Suppress("LongParameterList")
@Mockable
internal interface GroupConversationCreator {

    suspend fun discardPendingMLSGroupCreation(conversationId: ConversationId): Boolean

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
}

/**
 * Implementation of [GroupConversationCreator].
 */
internal class GroupConversationCreatorImpl(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
) : GroupConversationCreator {

    override suspend fun invoke(
        name: String,
        userIdList: List<UserId>,
        options: CreateConversationParam
    ): ConversationCreationResult =
        syncManager.waitUntilLiveOrFailure().flatMap {
            currentClientIdProvider()
        }.mapLeft { CreateGroupConversationFailure(it) }.flatMap { clientId ->
            conversationGroupRepository.createGroupConversation(name, userIdList, options.copy(creatorClientId = clientId))
        }.onSuccess {
            refreshUsersWithoutMetadata()
        }.flatMap { conversation ->
            // TODO(qol): this can be done in one query, e.g. pass current time when inserting
            conversationRepository.updateConversationModifiedDate(conversation.id, DateTimeUtil.currentInstant())
                .mapLeft { CreateGroupConversationFailure(it) }
                .map { conversation }
        }.fold({ creationFailure ->
            when (val failure = creationFailure.cause.normalizeFederatedBackendConflict()) {
                is NetworkFailure.NoNetworkConnection -> {
                    ConversationCreationResult.SyncFailure
                }

                is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> {
                    val conversationId = creationFailure.conversationId
                    val fallbackId = conversationId?.takeUnless { discardPendingMLSGroupCreation(it) }
                    ConversationCreationResult.BackendConflictFailure(failure.domains, fallbackId)
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
        }, {
            newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(it)
            ConversationCreationResult.Success(it)
        })

    override suspend fun discardPendingMLSGroupCreation(conversationId: ConversationId): Boolean =
        conversationRepository.setConversationDeletedLocally(conversationId, true).fold(
            { failure ->
                kaliumLogger.w("Failed to discard pending MLS conversation: $failure")
                false
            },
            // A successful no-op means the local conversation is already absent.
            { true }
        )
}
