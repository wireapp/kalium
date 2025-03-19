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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.DateTimeUtil

/**
 * Creates a conversation.
 * Can be used to create a group conversation or a channel.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [Result].
 */
@Suppress("LongParameterList")
class CreateGroupConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
) {

    /**
     * @param name the name of the conversation
     * @param userIdList list of members
     * @param options settings that customise the conversation
     */
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): Result =
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
                    Result.SyncFailure
                }

                is NetworkFailure.FederatedBackendFailure.ConflictingBackends -> {
                    Result.BackendConflictFailure(it.domains)
                }

                else -> {
                    Result.UnknownFailure(it)
                }
            }
        }, {
            newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(it)
            Result.Success(it)
        })

    sealed interface Result {
        /**
         * Conversation created successfully.
         */
        class Success(
            /**
             * Details of the newly created conversation
             */
            val conversation: Conversation
        ) : Result

        /**
         * There was a failure trying to Sync with the server
         */
        data object SyncFailure : Result

        /**
         * Other, unknown failure.
         */
        class UnknownFailure(
            /**
             * The root cause of the failure
             */
            val cause: CoreFailure
        ) : Result

        class BackendConflictFailure(
            val domains: List<String>
        ) : Result
    }
}
