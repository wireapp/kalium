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

internal class ConversationAccessRoleListAdapter : ColumnAdapter<List<ConversationEntity.AccessRole>, String> {
    override fun decode(databaseValue: String): List<ConversationEntity.AccessRole> =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            databaseValue.split(SEPARATOR).map { ConversationEntity.AccessRole.valueOf(it) }
        }

    override fun encode(value: List<ConversationEntity.AccessRole>): String = value.joinToString(separator = SEPARATOR)

    private companion object {
        private const val SEPARATOR = ","
    }
}

internal class ConversationAccessListAdapter : ColumnAdapter<List<ConversationEntity.Access>, String> {
    override fun decode(databaseValue: String): List<ConversationEntity.Access> =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            databaseValue.split(SEPARATOR).map { ConversationEntity.Access.valueOf(it) }
        }

    override fun encode(value: List<ConversationEntity.Access>): String = value.joinToString(separator = SEPARATOR)

    private companion object {
        private const val SEPARATOR = ","
    }
}
