/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddPermissionTypeDTO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

fun ChannelAddPermission.toDaoChannelPermission(): ConversationEntity.ChannelAddPermission = when (this) {
    ChannelAddPermission.ADMINS -> ConversationEntity.ChannelAddPermission.ADMINS
    ChannelAddPermission.EVERYONE -> ConversationEntity.ChannelAddPermission.EVERYONE
}

fun ChannelAddPermissionTypeDTO.toModel(): ChannelAddPermission = when (this) {
    ChannelAddPermissionTypeDTO.ADMINS -> ChannelAddPermission.ADMINS
    ChannelAddPermissionTypeDTO.EVERYONE -> ChannelAddPermission.EVERYONE
}
