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
package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

internal object ConversationTypeAdapter : ColumnAdapter<ConversationEntity.Type, String> {
    override fun decode(databaseValue: String): ConversationEntity.Type = when (databaseValue) {
        SELF -> ConversationEntity.Type.SELF
        ONE_ON_ONE -> ConversationEntity.Type.ONE_ON_ONE
        GROUP -> ConversationEntity.Type.GROUP
        CHANNEL -> ConversationEntity.Type.CHANNEL
        MEETING -> ConversationEntity.Type.MEETING
        CONNECTION_PENDING -> ConversationEntity.Type.CONNECTION_PENDING
        else -> ConversationEntity.Type.Unknown(databaseValue)
    }

    override fun encode(value: ConversationEntity.Type): String = when (value) {
        ConversationEntity.Type.SELF -> SELF
        ConversationEntity.Type.ONE_ON_ONE -> ONE_ON_ONE
        ConversationEntity.Type.GROUP -> GROUP
        ConversationEntity.Type.CHANNEL -> CHANNEL
        ConversationEntity.Type.MEETING -> MEETING
        ConversationEntity.Type.CONNECTION_PENDING -> CONNECTION_PENDING
        is ConversationEntity.Type.Unknown -> value.name
    }

    private const val SELF = "SELF"
    private const val ONE_ON_ONE = "ONE_ON_ONE"
    private const val GROUP = "GROUP"
    private const val CHANNEL = "CHANNEL"
    private const val MEETING = "MEETING"
    private const val CONNECTION_PENDING = "CONNECTION_PENDING"
}
