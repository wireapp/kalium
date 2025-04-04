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
package com.wire.kalium.logic.feature.conversation.channel

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddUserPermission
import com.wire.kalium.logic.data.conversation.channel.ChannelRepository
import com.wire.kalium.logic.data.id.ConversationId

/**
 * Use case to update the channel permission.
 */
interface UpdateChannelAddUserPermissionUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): UpdateChannelAddUserPermissionUseCaseResult

    sealed class UpdateChannelAddUserPermissionUseCaseResult {
        data object Success : UpdateChannelAddUserPermissionUseCaseResult()
        data object Failure : UpdateChannelAddUserPermissionUseCaseResult()
    }
}

internal class UpdateChannelAddUserPermissionUseCaseImpl(
    private val channelRepository: ChannelRepository
) : UpdateChannelAddUserPermissionUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): UpdateChannelAddUserPermissionUseCase.UpdateChannelAddUserPermissionUseCaseResult =
        channelRepository.updateAddUserPermission(conversationId, channelAddUserPermission)
            .fold(
                { UpdateChannelAddUserPermissionUseCase.UpdateChannelAddUserPermissionUseCaseResult.Failure },
                { UpdateChannelAddUserPermissionUseCase.UpdateChannelAddUserPermissionUseCaseResult.Success }
            )
}
