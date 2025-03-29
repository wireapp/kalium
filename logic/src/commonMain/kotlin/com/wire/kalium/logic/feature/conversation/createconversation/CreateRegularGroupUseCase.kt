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

import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.user.UserId

/**
 * Use case to create a regular group conversation.
 * This is a wrapper around [GroupConversationCreator] that sets the group type to [ConversationOptions.GroupType.REGULAR_GROUP].
 * @param createGroupConversation the use case to create a group conversation
 */
class CreateRegularGroupUseCase(
    private val createGroupConversation: GroupConversationCreator
) {
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): ConversationCreationResult =
        createGroupConversation(name, userIdList, options.copy(groupType = ConversationOptions.GroupType.REGULAR_GROUP))
}
