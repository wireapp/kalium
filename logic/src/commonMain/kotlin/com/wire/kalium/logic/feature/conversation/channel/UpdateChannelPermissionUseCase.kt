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
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelPermission
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId

/**
 * Use case to update the channel permission.
 */
interface UpdateChannelPermissionUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        channelPermission: ChannelPermission
    ): UpdateChannelPermissionUseCaseResult

    sealed class UpdateChannelPermissionUseCaseResult {
        data object Success : UpdateChannelPermissionUseCaseResult()
        data object Failure : UpdateChannelPermissionUseCaseResult()
    }
}

internal class UpdateChannelPermissionUseCaseImpl(
    val conversationRepository: ConversationRepository
) : UpdateChannelPermissionUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        channelPermission: ChannelPermission
    ): UpdateChannelPermissionUseCase.UpdateChannelPermissionUseCaseResult =
        conversationRepository.updateChannelPermission(conversationId, channelPermission)
            .fold(
                { UpdateChannelPermissionUseCase.UpdateChannelPermissionUseCaseResult.Failure },
                { UpdateChannelPermissionUseCase.UpdateChannelPermissionUseCaseResult.Success }
            )
}
