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

@file:OptIn(ExperimentalSerializationApi::class)

package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMembers @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("user_ids") val userIds: List<String>,
    @EncodeDefault @SerialName("users") val users: List<ConversationMemberDTO.Other> = emptyList()
)

@Serializable
data class ConversationMemberRemovedDTO(
    @SerialName("qualified_user_ids") val qualifiedUserIds: List<UserId>,
    @SerialName("reason") val reason: MemberLeaveReasonDTO = MemberLeaveReasonDTO.LEFT
)

@Serializable
data class ConversationRoleChange(
    @SerialName("target") val user: String,
    @SerialName("qualified_target") val qualifiedUserId: UserId,
    @SerialName("conversation_role") val role: String?,
    @SerialName("otr_muted_ref") val mutedRef: String?,
    @SerialName("otr_muted_status") val mutedStatus: Int?,
    @SerialName("otr_archived") val isArchiving: Boolean?,
    @SerialName("otr_archived_ref") val archivedRef: String?,
)

@Serializable
data class ConversationNameUpdateEvent(
    @SerialName("name") val conversationName: String,
)
