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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.first

/**
 * This use case will update the access and access role configuration of a conversation.
 * So we can operate and allow or not operation based on these roles:
 * - [Conversation.isGuestAllowed]
 * - [Conversation.isServicesAllowed]
 * - [Conversation.isTeamGroup]
 * - [Conversation.isNonTeamMemberAllowed]
 * @see Conversation.AccessRole
 *
 * and based on access
 * - [Conversation.Access.CODE]
 * - [Conversation.Access.INVITE]
 * - [Conversation.Access.LINK]
 * - [Conversation.Access.PRIVATE]
 * - [Conversation.Access.SELF_INVITE]
 *
 * @see Conversation.Access
 */

class UpdateConversationAccessRoleUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        accessRoles: Set<Conversation.AccessRole>,
        access: Set<Conversation.Access>,
    ): Result {

        syncManager.waitUntilLiveOrFailure().flatMap {
            if (!accessRoles.contains(Conversation.AccessRole.GUEST)
                && conversationGroupRepository.observeGuestRoomLink(conversationId).first() != null
            ) {
                conversationGroupRepository.revokeGuestRoomLink(conversationId)
            } else {
                Either.Right(Unit)
            }
        }

        return conversationRepository
            .updateAccessInfo(conversationId, access, accessRoles)
            .fold({
                Result.Failure(it)
            }, {
                Result.Success
            })
    }

    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}
