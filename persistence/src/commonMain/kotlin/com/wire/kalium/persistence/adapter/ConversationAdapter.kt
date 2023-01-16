package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.ConversationEntity

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
