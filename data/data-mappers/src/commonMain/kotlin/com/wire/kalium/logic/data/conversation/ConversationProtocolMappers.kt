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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.conversation.ConversationEntity.Protocol
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun Conversation.Protocol.toDao(): Protocol = when (this) {
    Conversation.Protocol.PROTEUS -> Protocol.PROTEUS
    Conversation.Protocol.MIXED -> Protocol.MIXED
    Conversation.Protocol.MLS -> Protocol.MLS
}

@InternalKaliumApi
public fun Protocol.toModel(): Conversation.Protocol = when (this) {
    Protocol.PROTEUS -> Conversation.Protocol.PROTEUS
    Protocol.MIXED -> Conversation.Protocol.MIXED
    Protocol.MLS -> Conversation.Protocol.MLS
}
