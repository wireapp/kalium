/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.monkeys

import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase

class DefaultConversationCreation : ConversationCreation {
    override suspend fun invoke(
        monkeyGroups: List<List<Monkey>>,
        protocol: ConversationOptions.Protocol
    ): List<MonkeyConversation> = monkeyGroups.map { group ->
        val groupCreator = group.first()
        val userScope = groupCreator.operationScope

        val conversationResult = userScope.conversations.createGroupConversation(
            name = "By Monkey '${groupCreator.user.email}'",
            userIdList = group.map { it.user.userId },
            options = ConversationOptions(protocol = protocol)
        )

        if (conversationResult !is CreateGroupConversationUseCase.Result.Success) {
            val cause = (conversationResult as? CreateGroupConversationUseCase.Result.UnknownFailure)?.cause
            error("Failed to create conversation $conversationResult; Cause = $cause")
        }
        MonkeyConversation(groupCreator, group, conversationResult.conversation)
    }
}
