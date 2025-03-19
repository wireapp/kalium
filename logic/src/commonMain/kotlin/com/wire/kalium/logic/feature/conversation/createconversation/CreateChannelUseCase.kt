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
import com.wire.kalium.logic.feature.conversation.createconversation.CreateGroupConversationUseCase.Result
import com.wire.kalium.logic.data.user.UserId

/**
 * Use case to create a channel.
 */
class CreateChannelUseCase internal constructor(
    private val createGroupConversation: CreateGroupConversationUseCase
) {
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): Result =
        createGroupConversation.invoke(name, userIdList, options.copy(groupType = ConversationOptions.GroupType.CHANNEL))
}
