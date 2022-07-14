package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter

class ConversationAccessRoleListAdapter : ColumnAdapter<List<ConversationEntity.AccessRole>, String> {
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

class ConversationAccessListAdapter : ColumnAdapter<List<ConversationEntity.Access>, String> {
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
