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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationDAOTest : BaseDatabaseTest() {
    private lateinit var migrationDAO: MigrationDAO
    private lateinit var conversationDAO: ConversationDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        migrationDAO = db.migrationDAO
        conversationDAO = db.conversationDAO
    }

    @Test
    fun givenConversationAlreadyStored_whenInsertingFromMigration_thenIgnore() = runTest {
        val conversation = newConversationEntity(id = "conversation_id").copy(type = ConversationEntity.Type.GROUP, name = "conv name")
        val conversationFromMigration = conversation.copy(type = ConversationEntity.Type.ONE_ON_ONE, name = "migration name")

        conversationDAO.insertConversation(conversation)
        conversationDAO.getConversationDetailsById(conversation.id).also {
            assertEquals(conversation.type, it?.type)
            assertEquals(conversation.name, it?.name)
        }

        migrationDAO.insertConversation(listOf(conversationFromMigration))
        conversationDAO.getConversationDetailsById(conversationFromMigration.id).also {
            assertEquals(conversation.type, it?.type)
            assertEquals(conversation.name, it?.name)
        }
    }
}
