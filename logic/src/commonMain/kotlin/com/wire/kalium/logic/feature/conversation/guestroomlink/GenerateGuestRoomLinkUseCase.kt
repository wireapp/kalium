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

package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler

/**
 * Use this use case to generate a new guest room link for a team conversation
 */
interface GenerateGuestRoomLinkUseCase {
    suspend operator fun invoke(conversationId: ConversationId, password: String?): GenerateGuestRoomLinkResult
}

class GenerateGuestRoomLinkUseCaseImpl internal constructor(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val codeUpdatedHandler: CodeUpdatedHandler,
) : GenerateGuestRoomLinkUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        password: String?
    ): GenerateGuestRoomLinkResult =
        conversationGroupRepository.generateGuestRoomLink(conversationId, password)
            .onSuccess {
                val event = Event.Conversation.CodeUpdated(
                    conversationId = it.qualifiedConversation.toModel(),
                    code = it.data.code,
                    id = uuid4().toString(),
                    isPasswordProtected = it.data.hasPassword,
                    key = it.data.key,
                    uri = it.data.uri
                )
                codeUpdatedHandler.handle(event)
            }
            .fold(
                GenerateGuestRoomLinkResult::Failure,
                { GenerateGuestRoomLinkResult.Success }
            )
}

sealed interface GenerateGuestRoomLinkResult {
    data object Success : GenerateGuestRoomLinkResult
    data class Failure(val cause: NetworkFailure) : GenerateGuestRoomLinkResult
}
