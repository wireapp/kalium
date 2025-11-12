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
package com.wire.kalium.logic.feature.conversation.apps

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.feature.conversation.UpdateConversationAccessRoleUseCase

/**
 * Use case to change access for apps in a conversation.
 * It updates the access roles and access of the conversation for apps (old service bots)
 * and inserts a system message indicating the change.
 */
class ChangeAccessForAppsInConversationUseCase internal constructor(
    private val updateConversationAccessRole: UpdateConversationAccessRoleUseCase,
    private val systemMessageInserter: SystemMessageInserter,
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        accessRoles: Set<Conversation.AccessRole>,
        access: Set<Conversation.Access>,
    ): UpdateConversationAccessRoleUseCase.Result {
        val result = updateConversationAccessRole(
            conversationId = conversationId,
            accessRoles = accessRoles,
            access = access
        )

        when (result) {
            is UpdateConversationAccessRoleUseCase.Result.Failure -> {
                // No system message is inserted on failure
            }

            is UpdateConversationAccessRoleUseCase.Result.Success -> {
                systemMessageInserter.insertConversationAppsAccessChanged(
                    conversationId = conversationId,
                    isAppsAccessEnabled = accessRoles.contains(Conversation.AccessRole.SERVICE)
                )
            }
        }

        return result
    }
}
