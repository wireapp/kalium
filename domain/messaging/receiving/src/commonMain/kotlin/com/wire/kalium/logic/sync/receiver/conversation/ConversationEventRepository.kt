/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.InternalKaliumApi

/** Access-model mapping required by incoming conversation events. */
@InternalKaliumApi
public interface ConversationEventAccessMapper {
    public fun toAccessEntity(access: Set<Conversation.Access>): List<ConversationEntity.Access>
    public fun toAccessRoleEntity(accessRoles: Set<Conversation.AccessRole>): List<ConversationEntity.AccessRole>
}

/** Default access-model mapping shared by continuous and bounded event processing. */
@InternalKaliumApi
public object ConversationEventAccessMapperImpl : ConversationEventAccessMapper {
    override fun toAccessEntity(access: Set<Conversation.Access>): List<ConversationEntity.Access> = access.map {
        when (it) {
            Conversation.Access.PRIVATE -> ConversationEntity.Access.PRIVATE
            Conversation.Access.INVITE -> ConversationEntity.Access.INVITE
            Conversation.Access.SELF_INVITE -> ConversationEntity.Access.SELF_INVITE
            Conversation.Access.LINK -> ConversationEntity.Access.LINK
            Conversation.Access.CODE -> ConversationEntity.Access.CODE
        }
    }

    override fun toAccessRoleEntity(accessRoles: Set<Conversation.AccessRole>): List<ConversationEntity.AccessRole> = accessRoles.map {
        when (it) {
            Conversation.AccessRole.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
            Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
            Conversation.AccessRole.GUEST -> ConversationEntity.AccessRole.GUEST
            Conversation.AccessRole.SERVICE -> ConversationEntity.AccessRole.SERVICE
            Conversation.AccessRole.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
        }
    }
}

/** Local channel-permission operation required by incoming conversation events. */
@InternalKaliumApi
public fun interface ChannelAddPermissionRepository {
    public suspend fun updateChannelAddPermissionLocally(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission,
    ): Either<CoreFailure, Unit>
}

/** Local persistence operations required by the simple incoming-conversation handlers. */
@InternalKaliumApi
public interface ConversationEventRepository : ChannelAddPermissionRepository {
    public suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode,
    ): Either<StorageFailure, Unit>

    public suspend fun updateMessageTimer(
        conversationId: ConversationId,
        messageTimer: Long?,
    ): Either<StorageFailure, Unit>

    public suspend fun updateAccess(
        conversationId: ConversationId,
        access: Set<Conversation.Access>,
        accessRoles: Set<Conversation.AccessRole>,
        onAppsAccessChanged: suspend (isEnabled: Boolean) -> Unit,
    ): Either<StorageFailure, Unit>

    public suspend fun updateGuestRoomLink(
        conversationId: ConversationId,
        link: String,
        isPasswordProtected: Boolean,
    ): Either<StorageFailure, Unit>

    public suspend fun deleteGuestRoomLink(
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit>
}

/** DAO-backed conversation-event persistence shared by continuous and bounded event processing. */
@InternalKaliumApi
public class ConversationEventRepositoryImpl public constructor(
    private val conversationDAO: ConversationDAO,
    private val accessMapper: ConversationEventAccessMapper = ConversationEventAccessMapperImpl,
) : ConversationEventRepository {

    override suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            conversationId.toDao(),
            when (receiptMode) {
                Conversation.ReceiptMode.DISABLED -> ConversationEntity.ReceiptMode.DISABLED
                Conversation.ReceiptMode.ENABLED -> ConversationEntity.ReceiptMode.ENABLED
            },
        )
    }

    override suspend fun updateMessageTimer(
        conversationId: ConversationId,
        messageTimer: Long?,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateMessageTimer(conversationId.toDao(), messageTimer)
    }

    override suspend fun updateAccess(
        conversationId: ConversationId,
        access: Set<Conversation.Access>,
        accessRoles: Set<Conversation.AccessRole>,
        onAppsAccessChanged: suspend (isEnabled: Boolean) -> Unit,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        val newAccess = accessMapper.toAccessEntity(access)
        val newAccessRoles = accessMapper.toAccessRoleEntity(accessRoles)
        val oldAccessRoles = conversationDAO.getConversationById(conversationId.toDao())?.accessRole
        val hadServiceRole = oldAccessRoles?.contains(ConversationEntity.AccessRole.SERVICE) == true
        val hasServiceRoleNow = newAccessRoles.contains(ConversationEntity.AccessRole.SERVICE)

        conversationDAO.updateAccess(
            conversationID = conversationId.toDao(),
            accessList = newAccess,
            accessRoleList = newAccessRoles,
        )

        when {
            hadServiceRole && !hasServiceRoleNow -> onAppsAccessChanged(false)
            !hadServiceRole && hasServiceRoleNow -> onAppsAccessChanged(true)
        }
    }

    override suspend fun updateChannelAddPermissionLocally(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateChannelAddPermission(
            conversationId.toDao(),
            when (channelAddPermission) {
                ChannelAddPermission.ADMINS -> ConversationEntity.ChannelAddPermission.ADMINS
                ChannelAddPermission.EVERYONE -> ConversationEntity.ChannelAddPermission.EVERYONE
            },
        )
    }

    override suspend fun updateGuestRoomLink(
        conversationId: ConversationId,
        link: String,
        isPasswordProtected: Boolean,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateGuestRoomLink(conversationId.toDao(), link, isPasswordProtected)
    }

    override suspend fun deleteGuestRoomLink(
        conversationId: ConversationId,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.deleteGuestRoomLink(conversationId.toDao())
    }
}
