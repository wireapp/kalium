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
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.SelectMembersForConversations
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.member.MemberEntity

data class ConversationDetailsWithMembersEntity(
    val conversationViewEntity: ConversationViewEntity,
    val members: List<MemberWithUserEntity>,
)

data class MemberWithUserEntity(val user: UserEntity, val role: MemberEntity.Role)

fun SelectMembersForConversations.toMemberWithUserEntity() = MemberWithUserEntity(
    user = UserEntity(
        id = qualified_id,
        name = name,
        handle = handle,
        email = email,
        phone = phone,
        accentId = accent_id,
        team = team,
        connectionStatus = connection_status,
        previewAssetId = preview_asset_id,
        completeAssetId = complete_asset_id,
        availabilityStatus = user_availability_status,
        userType = user_type,
        botService = bot_service,
        deleted = deleted,
        hasIncompleteMetadata = incomplete_metadata,
        expiresAt = expires_at,
        defederated = defederated,
        supportedProtocols = supported_protocols,
        activeOneOnOneConversationId = active_one_on_one_conversation_id,
    ),
    role = role,
)
