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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
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
interface GroupConversationCreator {

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
        }.flatMap { clientId ->
            conversationGroupRepository.createGroupConversation(name, userIdList, options.copy(creatorClientId = clientId))
        }.onSuccess {
            refreshUsersWithoutMetadata()
        }.flatMap { conversation ->
            // TODO(qol): this can be done in one query, e.g. pass current time when inserting
            conversationRepository.updateConversationModifiedDate(conversation.id, DateTimeUtil.currentInstant())
                .map { conversation }
        }.fold({
            when (it) {
                is NetworkFailure.NoNetworkConnection -> {
                    ConversationCreationResult.SyncFailure
                }

                is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> {
                    ConversationCreationResult.BackendConflictFailure(it.domains)
                }

                is NetworkFailure.ServerMiscommunication -> {
                    val exception = it.kaliumException
                    if (exception is KaliumException.InvalidRequestError && exception.isOperationDenied()
                    ) {
                        ConversationCreationResult.Forbidden
                    } else {
                        ConversationCreationResult.UnknownFailure(it)
                    }
                }

                else -> {
                    ConversationCreationResult.UnknownFailure(it)
                }
            }
        }, {
            newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(it)
            ConversationCreationResult.Success(it)
        })
}
