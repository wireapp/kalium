/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageMapper
import com.wire.kalium.persistence.dao.message.MessageTimelineLoadResultEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineRequestEntity
import com.wire.kalium.persistence.db.DbInvalidationController
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UserTextStatusMigrationTest {
    @Test
    fun freshAndUpgradedDatabasesExposeStatusInProfilesAndBothMessageApis() = runTest {
        for (upgrade in listOf(false, true)) {
            val file = File.createTempFile("user-status-migration-", ".db")
            if (upgrade) File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
            val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
            try {
                if (upgrade) {
                    object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 124, 164) }
                } else UserDatabase.Schema.awaitCreate(driver)
                driver.execute(null, "INSERT INTO User(qualified_id, name) VALUES ('sender@domain', 'Sender')", 0)
                driver.execute(null, "INSERT INTO SelfUser(id) VALUES ('sender@domain')", 0)
                driver.execute(null, """
                    INSERT INTO Conversation(qualified_id,type,mls_group_state,protocol,creator_id,last_modified_date,
                        access_list,access_role_list,mls_cipher_suite)
                    VALUES ('conversation@domain','GROUP','ESTABLISHED','PROTEUS','sender@domain',100,'[]','[]','UNKNOWN')
                """.trimIndent(), 0)
                driver.execute(null, """
                    INSERT INTO Message(id,conversation_id,content_type,creation_date,sender_user_id,sender_client_id,status,visibility)
                    VALUES ('message','conversation@domain','TEXT',100,'sender@domain','client','SENT','VISIBLE')
                """.trimIndent(), 0)
                driver.execute(null, """
                    INSERT INTO MessageTextContent(message_id,conversation_id,text_body,is_quoting_self,is_quote_verified)
                    VALUES ('message','conversation@domain','Hello',0,0)
                """.trimIndent(), 0)
                if (upgrade) UserDatabase.Schema.awaitMigrate(driver, 164, UserDatabase.Schema.version)
                val userId = QualifiedIDEntity("sender", "domain")
                val conversationId = QualifiedIDEntity("conversation", "domain")
                val builder = UserDatabaseBuilder(
                    userId, driver, StandardTestDispatcher(testScheduler), PlatformDatabaseData(StorageData.FileBacked(file)), false,
                    DbInvalidationController(false) { driver.notifyListeners(it) },
                )
                assertNull(builder.userDAO.getUserDetailsByQualifiedID(userId)?.textStatus)
                builder.userDAO.updateUserTextStatus(userId, "🎧 In a meeting")
                assertEquals("🎧 In a meeting", builder.userDAO.getUserDetailsByQualifiedID(userId)?.textStatus)
                val message = builder.database.messagesQueries.selectByConversationIdAndVisibility(
                    conversationId, MessageEntity.Visibility.entries, 1, 0, MessageMapper::toEntityMessageFromView
                ).awaitAsList().single()
                assertEquals("🎧 In a meeting", message.sender?.textStatus)
                builder.messageTimelineDAO.open(conversationId).use { session ->
                    val page = assertIs<MessageTimelineLoadResultEntity.Success>(session.load(MessageTimelineRequestEntity.Latest, 1)).page
                    assertEquals("🎧 In a meeting", page.users.getValue(userId).text_status)
                }
                builder.userDAO.updateUserTextStatus(userId, null)
                assertNull(builder.userDAO.getUserDetailsByQualifiedID(userId)?.textStatus)
                assertEquals(emptyList(), driver.executeQuery(null, "PRAGMA foreign_key_check", { cursor ->
                    val violations = buildList { while (cursor.next().value) add(cursor.getString(0)) }
                    QueryResult.Value(violations)
                }, 0).value)
            } finally {
                driver.close()
                file.delete()
            }
        }
    }
}
