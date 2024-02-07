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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.persistence.dao.message.LocalId

/**
 * Renames a conversation by its ID.
 */
interface RenameConversationUseCase {
    /**
     * @param conversationId the conversation id to rename
     * @param conversationName the new conversation name
     */
    suspend operator fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult
}

internal class RenameConversationUseCaseImpl(
    val conversationRepository: ConversationRepository,
    val persistMessage: PersistMessageUseCase,
    private val renamedConversationEventHandler: RenamedConversationEventHandler,
    val selfUserId: UserId,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId)
) : RenameConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult {
        return conversationRepository.changeConversationName(conversationId, conversationName)
            .onSuccess { response ->
                if (response is ConversationRenameResponse.Changed)
                    renamedConversationEventHandler.handle(
                        eventMapper.conversationRenamed(LocalId.generate(), response.event)
                    )
            }
            .fold({
                RenamingResult.Failure(it)
            }, {
                RenamingResult.Success
            })
    }
}

sealed class RenamingResult {
    data object Success : RenamingResult()
    data class Failure(val coreFailure: CoreFailure) : RenamingResult()
}
