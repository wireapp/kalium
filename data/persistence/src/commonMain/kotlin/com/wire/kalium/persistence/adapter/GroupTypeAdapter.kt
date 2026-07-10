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

package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

internal object GroupTypeAdapter : ColumnAdapter<ConversationEntity.GroupType, String> {
    override fun decode(databaseValue: String): ConversationEntity.GroupType = when (databaseValue) {
        GROUP -> ConversationEntity.GroupType.Group
        CHANNEL -> ConversationEntity.GroupType.Channel
        MEETING -> ConversationEntity.GroupType.Meeting
        else -> ConversationEntity.GroupType.Unknown(databaseValue)
    }

    override fun encode(value: ConversationEntity.GroupType): String = when (value) {
        is ConversationEntity.GroupType.Group -> GROUP
        is ConversationEntity.GroupType.Channel -> CHANNEL
        is ConversationEntity.GroupType.Meeting -> MEETING
        is ConversationEntity.GroupType.Unknown -> value.name
    }

    private const val GROUP = "GROUP"
    private const val CHANNEL = "CHANNEL"
    private const val MEETING = "MEETING"
}
