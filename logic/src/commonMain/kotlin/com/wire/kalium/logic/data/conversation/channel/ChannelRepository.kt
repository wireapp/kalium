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
package com.wire.kalium.logic.data.conversation.channel

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddUserPermission
import com.wire.kalium.logic.data.conversation.toApi
import com.wire.kalium.logic.data.conversation.toDaoChannelPermission
import com.wire.kalium.logic.data.conversation.toModelChannelPermission
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddUserPermissionResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.dao.conversation.ConversationDAO

interface ChannelRepository {
    suspend fun getAddUserPermission(conversationId: ConversationId): Either<StorageFailure, ChannelAddUserPermission>

    suspend fun updateAddUserPermissionLocally(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<CoreFailure, Unit>

    suspend fun updateAddUserPermissionRemotely(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<NetworkFailure, UpdateChannelAddUserPermissionResponse>

    suspend fun updateAddUserPermission(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<CoreFailure, Unit>
}

internal class ChannelDataSource internal constructor(
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
) : ChannelRepository {

    override suspend fun getAddUserPermission(conversationId: ConversationId): Either<StorageFailure, ChannelAddUserPermission> =
        wrapStorageRequest {
            conversationDAO.getChannelAddUserPermission(conversationId.toDao()).toModelChannelPermission()
        }


    override suspend fun updateAddUserPermissionLocally(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateChannelAddUserPermission(
            conversationId.toDao(),
            channelAddUserPermission.toDaoChannelPermission()
        )
    }

    override suspend fun updateAddUserPermissionRemotely(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<NetworkFailure, UpdateChannelAddUserPermissionResponse> = wrapApiRequest {
        conversationApi.updateChannelAddUserPermission(
            conversationId = conversationId.toApi(),
            channelAddUserPermission = channelAddUserPermission.toApi()
        )
    }

    override suspend fun updateAddUserPermission(
        conversationId: ConversationId,
        channelAddUserPermission: ChannelAddUserPermission
    ): Either<CoreFailure, Unit> = updateAddUserPermissionRemotely(conversationId, channelAddUserPermission).flatMap {
        when (it) {
            is UpdateChannelAddUserPermissionResponse.AddUserPermissionUnchanged -> {
                Either.Right(Unit)
            }

            is UpdateChannelAddUserPermissionResponse.AddUserPermissionUpdated -> {
                updateAddUserPermissionLocally(conversationId, channelAddUserPermission)
            }
        }
    }
}
