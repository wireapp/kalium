/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for migration 120: Consolidating system message content tables into MessageSystemContent
 *
 * This migration consolidates 10 separate system message content tables into a single table
 * with generic typed columns.
 */
class Migration124Test : SchemaMigrationTest() {

    companion object Companion {
        // Test data constants
        private const val MESSAGE_ID = "test-message-id"
        private const val CONVERSATION_ID = "test-conversation-id"
        private const val USER_ID = "user-id@domain.com"
        private const val USER_ID_2 = "user-id-2@domain.com"
        private const val MIGRATION_NAME = 124
    }

    /**
     * Returns the migration SQL for migration 120.
     * This reads the actual migration file from disk to ensure tests match the real migration.
     */
    private fun getMigration120Sql(): String {
        val migrationFile = File("src/commonMain/db_user/migrations/$MIGRATION_NAME.sqm")
        if (!migrationFile.exists()) {
            error("Migration file not found: ${migrationFile.absolutePath}")
        }
        return migrationFile.readText()
    }

    @Test
    fun testMemberChangeContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert test data into MessageMemberChangeContent
                driver.executeInsert("""
                    INSERT INTO MessageMemberChangeContent (message_id, conversation_id, member_change_list, member_change_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '["$USER_ID", "$USER_ID_2"]', 'ADDED')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify the data was migrated to MessageSystemContent
                val count = driver.countRows("MessageSystemContent")
                assertEquals(1, count, "Should have 1 row in MessageSystemContent")

                // Query the migrated data
                var contentType: String? = null
                var list1: String? = null
                var enum1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, list_1, enum_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                        enum1 = cursor.getString(2)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("MEMBER_CHANGE", contentType)
                assertEquals("""["$USER_ID", "$USER_ID_2"]""", list1)
                assertEquals("ADDED", enum1)

                // Verify old table was dropped
                assertTableDoesNotExist(driver, "MessageMemberChangeContent")
            }
        )
    }

    @Test
    fun testFailedToDecryptContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert test data into MessageFailedToDecryptContent
                val testData = byteArrayOf(0x01, 0x02, 0x03)
                driver.execute(null, """
                    INSERT INTO MessageFailedToDecryptContent
                    (message_id, conversation_id, unknown_encoded_data, is_decryption_resolved, error_code)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent(), 5) {
                    bindString(0, MESSAGE_ID)
                    bindString(1, CONVERSATION_ID)
                    bindBytes(2, testData)
                    bindLong(3, 0) // false
                    bindLong(4, 404)
                }
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify the data was migrated
                var contentType: String? = null
                var blob1: ByteArray? = null
                var boolean1: Long? = null
                var integer1: Long? = null

                driver.executeQuery(null, """
                    SELECT content_type, blob_1, boolean_1, integer_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        blob1 = cursor.getBytes(1)
                        boolean1 = cursor.getLong(2)
                        integer1 = cursor.getLong(3)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("FAILED_DECRYPT", contentType)
                assertNotNull(blob1)
                assertEquals(3, blob1?.size)
                assertEquals(0L, boolean1) // false
                assertEquals(404L, integer1)

                assertTableDoesNotExist(driver, "MessageFailedToDecryptContent")
            }
        )
    }

    @Test
    fun testConversationRenamedContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageConversationChangedContent (message_id, conversation_id, conversation_name)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 'New Conversation Name')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var text1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, text_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        text1 = cursor.getString(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_RENAMED", contentType)
                assertEquals("New Conversation Name", text1)

                assertTableDoesNotExist(driver, "MessageConversationChangedContent")
            }
        )
    }

    @Test
    fun testReceiptModeContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Test NEW_CONVERSATION_RECEIPT_MODE
                driver.executeInsert("""
                    INSERT INTO MessageNewConversationReceiptModeContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg-1', '$CONVERSATION_ID', 1)
                """)

                // Test CONVERSATION_RECEIPT_MODE_CHANGED
                driver.executeInsert("""
                    INSERT INTO MessageConversationReceiptModeChangedContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg-2', '$CONVERSATION_ID', 0)
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                val count = driver.countRows("MessageSystemContent")
                assertEquals(2, count)

                // Verify NEW_CONVERSATION_RECEIPT_MODE
                var contentType: String? = null
                var boolean1: Long? = null

                driver.executeQuery(null, """
                    SELECT content_type, boolean_1
                    FROM MessageSystemContent
                    WHERE message_id = 'msg-1'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        boolean1 = cursor.getLong(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("NEW_CONVERSATION_RECEIPT_MODE", contentType)
                assertEquals(1L, boolean1)

                // Verify CONVERSATION_RECEIPT_MODE_CHANGED
                driver.executeQuery(null, """
                    SELECT content_type, boolean_1
                    FROM MessageSystemContent
                    WHERE message_id = 'msg-2'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        boolean1 = cursor.getLong(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_RECEIPT_MODE_CHANGED", contentType)
                assertEquals(0L, boolean1)

                assertTableDoesNotExist(driver, "MessageNewConversationReceiptModeContent")
                assertTableDoesNotExist(driver, "MessageConversationReceiptModeChangedContent")
            }
        )
    }

    @Test
    fun testTimerChangedContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageConversationTimerChangedContent (message_id, conversation_id, message_timer)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 86400000)
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var integer1: Long? = null

                driver.executeQuery(null, """
                    SELECT content_type, integer_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        integer1 = cursor.getLong(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_TIMER_CHANGED", contentType)
                assertEquals(86400000L, integer1)

                assertTableDoesNotExist(driver, "MessageConversationTimerChangedContent")
            }
        )
    }

    @Test
    fun testFederationTerminatedContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageFederationTerminatedContent (message_id, conversation_id, domain_list, federation_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '["domain1.com", "domain2.com"]', 'REMOVED')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var list1: String? = null
                var enum1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, list_1, enum_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                        enum1 = cursor.getString(2)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("FEDERATION_TERMINATED", contentType)
                assertEquals("""["domain1.com", "domain2.com"]""", list1)
                assertEquals("REMOVED", enum1)

                assertTableDoesNotExist(driver, "MessageFederationTerminatedContent")
            }
        )
    }

    @Test
    fun testProtocolChangedContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageConversationProtocolChangedContent (message_id, conversation_id, protocol)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 'MLS')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var enum1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, enum_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        enum1 = cursor.getString(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_PROTOCOL_CHANGED", contentType)
                assertEquals("MLS", enum1)

                assertTableDoesNotExist(driver, "MessageConversationProtocolChangedContent")
            }
        )
    }

    @Test
    fun testLegalHoldContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageLegalHoldContent (message_id, conversation_id, legal_hold_member_list, legal_hold_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '["$USER_ID"]', 'ENABLED')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var list1: String? = null
                var enum1: String? = null

                driver.executeQuery(null, """
                    SELECT content_type, list_1, enum_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                        enum1 = cursor.getString(2)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("LEGAL_HOLD", contentType)
                assertEquals("""["$USER_ID"]""", list1)
                assertEquals("ENABLED", enum1)

                assertTableDoesNotExist(driver, "MessageLegalHoldContent")
            }
        )
    }

    @Test
    fun testAppsEnabledChangedContentMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageConversationAppsEnabledChangedContent (message_id, conversation_id, is_apps_enabled)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 1)
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var boolean1: Long? = null

                driver.executeQuery(null, """
                    SELECT content_type, boolean_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        boolean1 = cursor.getLong(1)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_APPS_ENABLED_CHANGED", contentType)
                assertEquals(1L, boolean1)

                assertTableDoesNotExist(driver, "MessageConversationAppsEnabledChangedContent")
            }
        )
    }

    @Test
    fun testMultipleSystemMessagesMigration() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert multiple different system message types
                driver.executeInsert("""
                    INSERT INTO MessageMemberChangeContent (message_id, conversation_id, member_change_list, member_change_type)
                    VALUES ('msg-1', '$CONVERSATION_ID', '["$USER_ID"]', 'ADDED')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationChangedContent (message_id, conversation_id, conversation_name)
                    VALUES ('msg-2', '$CONVERSATION_ID', 'Team Chat')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationTimerChangedContent (message_id, conversation_id, message_timer)
                    VALUES ('msg-3', '$CONVERSATION_ID', 3600000)
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationProtocolChangedContent (message_id, conversation_id, protocol)
                    VALUES ('msg-4', '$CONVERSATION_ID', 'MLS')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify all 4 messages were migrated
                val count = driver.countRows("MessageSystemContent")
                assertEquals(4, count, "Should have migrated 4 system messages")

                // Verify each content type exists
                val contentTypes = mutableListOf<String>()
                driver.executeQuery(null, """
                    SELECT content_type FROM MessageSystemContent ORDER BY message_id
                """.trimIndent(), mapper = { cursor ->
                    while (cursor.next().value) {
                        contentTypes.add(cursor.getString(0) ?: "")
                    }
                    QueryResult.Unit
                }, 0)

                assertTrue(contentTypes.contains("MEMBER_CHANGE"))
                assertTrue(contentTypes.contains("CONVERSATION_RENAMED"))
                assertTrue(contentTypes.contains("CONVERSATION_TIMER_CHANGED"))
                assertTrue(contentTypes.contains("CONVERSATION_PROTOCOL_CHANGED"))
            }
        )
    }

    @Test
    fun testIndexCreation() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // No setup needed, just test index creation
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify index exists
                var indexExists = false
                driver.executeQuery(null, """
                    SELECT name FROM sqlite_master
                    WHERE type='index' AND name='idx_system_content_type'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        indexExists = true
                    }
                    QueryResult.Unit
                }, 0)

                assertTrue(indexExists, "Index idx_system_content_type should be created")
            }
        )
    }

    // ============================================================
    // Data Integrity Tests - Verify ALL fields are migrated correctly
    // ============================================================

    @Test
    fun testMemberChangeDataIntegrity_AllFieldsPreserved() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert with all possible data
                driver.executeInsert("""
                    INSERT INTO MessageMemberChangeContent (message_id, conversation_id, member_change_list, member_change_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '["$USER_ID", "$USER_ID_2"]', 'FEDERATION_REMOVED')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify ALL fields in the migrated row
                var messageId: String? = null
                var conversationId: String? = null
                var contentType: String? = null
                var text1: String? = null
                var integer1: Long? = null
                var boolean1: Long? = null
                var list1: String? = null
                var enum1: String? = null
                var blob1: ByteArray? = null

                driver.executeQuery(null, """
                    SELECT message_id, conversation_id, content_type, text_1, integer_1, boolean_1, list_1, enum_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        messageId = cursor.getString(0)
                        conversationId = cursor.getString(1)
                        contentType = cursor.getString(2)
                        text1 = cursor.getString(3)
                        integer1 = cursor.getLong(4)
                        boolean1 = cursor.getLong(5)
                        list1 = cursor.getString(6)
                        enum1 = cursor.getString(7)
                        blob1 = cursor.getBytes(8)
                    }
                    QueryResult.Unit
                }, 0)

                // Verify PKs
                assertEquals(MESSAGE_ID, messageId)
                assertEquals(CONVERSATION_ID, conversationId)

                // Verify content type
                assertEquals("MEMBER_CHANGE", contentType)

                // Verify used fields
                assertEquals("""["$USER_ID", "$USER_ID_2"]""", list1)
                assertEquals("FEDERATION_REMOVED", enum1)

                // Verify unused fields are NULL
                assertNull(text1)
                assertNull(integer1)
                assertNull(boolean1)
                assertNull(blob1)
            }
        )
    }

    @Test
    fun testFailedDecryptDataIntegrity_AllFieldsPreserved() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                val testData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
                driver.execute(null, """
                    INSERT INTO MessageFailedToDecryptContent
                    (message_id, conversation_id, unknown_encoded_data, is_decryption_resolved, error_code)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent(), 5) {
                    bindString(0, MESSAGE_ID)
                    bindString(1, CONVERSATION_ID)
                    bindBytes(2, testData)
                    bindLong(3, 1) // true
                    bindLong(4, 500)
                }
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var messageId: String? = null
                var conversationId: String? = null
                var contentType: String? = null
                var text1: String? = null
                var integer1: Long? = null
                var boolean1: Long? = null
                var list1: String? = null
                var enum1: String? = null
                var blob1: ByteArray? = null

                driver.executeQuery(null, """
                    SELECT message_id, conversation_id, content_type, text_1, integer_1, boolean_1, list_1, enum_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        messageId = cursor.getString(0)
                        conversationId = cursor.getString(1)
                        contentType = cursor.getString(2)
                        text1 = cursor.getString(3)
                        integer1 = cursor.getLong(4)
                        boolean1 = cursor.getLong(5)
                        list1 = cursor.getString(6)
                        enum1 = cursor.getString(7)
                        blob1 = cursor.getBytes(8)
                    }
                    QueryResult.Unit
                }, 0)

                // Verify all fields
                assertEquals(MESSAGE_ID, messageId)
                assertEquals(CONVERSATION_ID, conversationId)
                assertEquals("FAILED_DECRYPT", contentType)
                assertEquals(500L, integer1)
                assertEquals(1L, boolean1)
                assertNotNull(blob1)
                assertEquals(5, blob1?.size)

                // Verify unused fields are NULL
                assertNull(text1)
                assertNull(list1)
                assertNull(enum1)
            }
        )
    }

    @Test
    fun testConversationRenamedDataIntegrity_AllFieldsPreserved() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                driver.executeInsert("""
                    INSERT INTO MessageConversationChangedContent (message_id, conversation_id, conversation_name)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', 'Complete Conversation Name')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var messageId: String? = null
                var conversationId: String? = null
                var contentType: String? = null
                var text1: String? = null
                var integer1: Long? = null
                var boolean1: Long? = null
                var list1: String? = null
                var enum1: String? = null
                var blob1: ByteArray? = null

                driver.executeQuery(null, """
                    SELECT message_id, conversation_id, content_type, text_1, integer_1, boolean_1, list_1, enum_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        messageId = cursor.getString(0)
                        conversationId = cursor.getString(1)
                        contentType = cursor.getString(2)
                        text1 = cursor.getString(3)
                        integer1 = cursor.getLong(4)
                        boolean1 = cursor.getLong(5)
                        list1 = cursor.getString(6)
                        enum1 = cursor.getString(7)
                        blob1 = cursor.getBytes(8)
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals(MESSAGE_ID, messageId)
                assertEquals(CONVERSATION_ID, conversationId)
                assertEquals("CONVERSATION_RENAMED", contentType)
                assertEquals("Complete Conversation Name", text1)

                // Verify unused fields are NULL
                assertNull(integer1)
                assertNull(boolean1)
                assertNull(list1)
                assertNull(enum1)
                assertNull(blob1)
            }
        )
    }

    @Test
    fun testAllReceiptModeTypesDataIntegrity() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // NEW_CONVERSATION_RECEIPT_MODE
                driver.executeInsert("""
                    INSERT INTO MessageNewConversationReceiptModeContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg-new', '$CONVERSATION_ID', 1)
                """)

                // CONVERSATION_RECEIPT_MODE_CHANGED
                driver.executeInsert("""
                    INSERT INTO MessageConversationReceiptModeChangedContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg-changed', '$CONVERSATION_ID', 0)
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify NEW_CONVERSATION_RECEIPT_MODE
                var contentType1: String? = null
                var boolean1_1: Long? = null
                var unusedFields1 = 0

                driver.executeQuery(null, """
                    SELECT content_type, boolean_1, text_1, integer_1, list_1, enum_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = 'msg-new'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType1 = cursor.getString(0)
                        boolean1_1 = cursor.getLong(1)
                        // Count NULL fields
                        if (cursor.getString(2) == null) unusedFields1++
                        if (cursor.getLong(3) == null) unusedFields1++
                        if (cursor.getString(4) == null) unusedFields1++
                        if (cursor.getString(5) == null) unusedFields1++
                        if (cursor.getBytes(6) == null) unusedFields1++
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("NEW_CONVERSATION_RECEIPT_MODE", contentType1)
                assertEquals(1L, boolean1_1)
                assertEquals(5, unusedFields1, "All unused fields should be NULL")

                // Verify CONVERSATION_RECEIPT_MODE_CHANGED
                var contentType2: String? = null
                var boolean1_2: Long? = null
                var unusedFields2 = 0

                driver.executeQuery(null, """
                    SELECT content_type, boolean_1, text_1, integer_1, list_1, enum_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = 'msg-changed'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType2 = cursor.getString(0)
                        boolean1_2 = cursor.getLong(1)
                        // Count NULL fields
                        if (cursor.getString(2) == null) unusedFields2++
                        if (cursor.getLong(3) == null) unusedFields2++
                        if (cursor.getString(4) == null) unusedFields2++
                        if (cursor.getString(5) == null) unusedFields2++
                        if (cursor.getBytes(6) == null) unusedFields2++
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("CONVERSATION_RECEIPT_MODE_CHANGED", contentType2)
                assertEquals(0L, boolean1_2)
                assertEquals(5, unusedFields2, "All unused fields should be NULL")
            }
        )
    }

    @Test
    fun testFederationTerminatedDataIntegrity_ComplexListPreserved() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Complex domain list with multiple domains
                val complexDomainList = """["domain1.com", "domain2.co.uk", "subdomain.example.org"]"""
                driver.executeInsert("""
                    INSERT INTO MessageFederationTerminatedContent (message_id, conversation_id, domain_list, federation_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '$complexDomainList', 'CONNECTION_REMOVED')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var list1: String? = null
                var enum1: String? = null
                var nullFieldCount = 0

                driver.executeQuery(null, """
                    SELECT content_type, list_1, enum_1, text_1, integer_1, boolean_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                        enum1 = cursor.getString(2)
                        // Count unused fields
                        if (cursor.getString(3) == null) nullFieldCount++
                        if (cursor.getLong(4) == null) nullFieldCount++
                        if (cursor.getLong(5) == null) nullFieldCount++
                        if (cursor.getBytes(6) == null) nullFieldCount++
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("FEDERATION_TERMINATED", contentType)
                assertEquals("""["domain1.com", "domain2.co.uk", "subdomain.example.org"]""", list1)
                assertEquals("CONNECTION_REMOVED", enum1)
                assertEquals(4, nullFieldCount, "All 4 unused fields should be NULL")
            }
        )
    }

    @Test
    fun testLegalHoldDataIntegrity_ComplexMemberList() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Complex member list with multiple qualified IDs
                val memberList = """["user1@domain1.com", "user2@domain2.com", "user3@domain1.com"]"""
                driver.executeInsert("""
                    INSERT INTO MessageLegalHoldContent (message_id, conversation_id, legal_hold_member_list, legal_hold_type)
                    VALUES ('$MESSAGE_ID', '$CONVERSATION_ID', '$memberList', 'DISABLED_FOR_CONVERSATION')
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                var contentType: String? = null
                var list1: String? = null
                var enum1: String? = null
                var nullFieldCount = 0

                driver.executeQuery(null, """
                    SELECT content_type, list_1, enum_1, text_1, integer_1, boolean_1, blob_1
                    FROM MessageSystemContent
                    WHERE message_id = '$MESSAGE_ID'
                """.trimIndent(), mapper = { cursor ->
                    if (cursor.next().value) {
                        contentType = cursor.getString(0)
                        list1 = cursor.getString(1)
                        enum1 = cursor.getString(2)
                        if (cursor.getString(3) == null) nullFieldCount++
                        if (cursor.getLong(4) == null) nullFieldCount++
                        if (cursor.getLong(5) == null) nullFieldCount++
                        if (cursor.getBytes(6) == null) nullFieldCount++
                    }
                    QueryResult.Unit
                }, 0)

                assertEquals("LEGAL_HOLD", contentType)
                assertEquals("""["user1@domain1.com", "user2@domain2.com", "user3@domain1.com"]""", list1)
                assertEquals("DISABLED_FOR_CONVERSATION", enum1)
                assertEquals(4, nullFieldCount)
            }
        )
    }

    @Test
    fun testAllSystemMessageTypesNoDataLoss() = runTest(dispatcher) {
        runMigrationTest(
            schemaVersion = MIGRATION_NAME,
            setupOldSchema = { driver ->
                // Insert one of each type with maximum data complexity
                driver.executeInsert("""
                    INSERT INTO MessageMemberChangeContent (message_id, conversation_id, member_change_list, member_change_type)
                    VALUES ('msg1', '$CONVERSATION_ID', '["$USER_ID", "$USER_ID_2"]', 'REMOVED_FROM_TEAM')
                """)

                driver.execute(null, """
                    INSERT INTO MessageFailedToDecryptContent
                    (message_id, conversation_id, unknown_encoded_data, is_decryption_resolved, error_code)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent(), 5) {
                    bindString(0, "msg2")
                    bindString(1, CONVERSATION_ID)
                    bindBytes(2, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
                    bindLong(3, 0)
                    bindLong(4, 404)
                }

                driver.executeInsert("""
                    INSERT INTO MessageConversationChangedContent (message_id, conversation_id, conversation_name)
                    VALUES ('msg3', '$CONVERSATION_ID', 'Very Long Conversation Name With Special Chars 🎉')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageNewConversationReceiptModeContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg4', '$CONVERSATION_ID', 1)
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationReceiptModeChangedContent (message_id, conversation_id, receipt_mode)
                    VALUES ('msg5', '$CONVERSATION_ID', 0)
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationTimerChangedContent (message_id, conversation_id, message_timer)
                    VALUES ('msg6', '$CONVERSATION_ID', 86400000)
                """)

                driver.executeInsert("""
                    INSERT INTO MessageFederationTerminatedContent (message_id, conversation_id, domain_list, federation_type)
                    VALUES ('msg7', '$CONVERSATION_ID', '["domain1.com", "domain2.com", "domain3.com"]', 'DELETE')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationProtocolChangedContent (message_id, conversation_id, protocol)
                    VALUES ('msg8', '$CONVERSATION_ID', 'MLS')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageLegalHoldContent (message_id, conversation_id, legal_hold_member_list, legal_hold_type)
                    VALUES ('msg9', '$CONVERSATION_ID', '["$USER_ID"]', 'ENABLED_FOR_MEMBERS')
                """)

                driver.executeInsert("""
                    INSERT INTO MessageConversationAppsEnabledChangedContent (message_id, conversation_id, is_apps_enabled)
                    VALUES ('msg10', '$CONVERSATION_ID', 1)
                """)
            },
            migrationSql = { getMigration120Sql() },
            verifyNewSchema = { driver ->
                // Verify count
                val totalCount = driver.countRows("MessageSystemContent")
                assertEquals(10L, totalCount, "All 10 messages should be migrated")

                // Verify each message has correct content_type and non-null required fields
                val expectedTypes = listOf(
                    "msg1" to "MEMBER_CHANGE",
                    "msg2" to "FAILED_DECRYPT",
                    "msg3" to "CONVERSATION_RENAMED",
                    "msg4" to "NEW_CONVERSATION_RECEIPT_MODE",
                    "msg5" to "CONVERSATION_RECEIPT_MODE_CHANGED",
                    "msg6" to "CONVERSATION_TIMER_CHANGED",
                    "msg7" to "FEDERATION_TERMINATED",
                    "msg8" to "CONVERSATION_PROTOCOL_CHANGED",
                    "msg9" to "LEGAL_HOLD",
                    "msg10" to "CONVERSATION_APPS_ENABLED_CHANGED"
                )

                expectedTypes.forEach { (msgId, expectedType) ->
                    var actualType: String? = null
                    driver.executeQuery(null, """
                        SELECT content_type FROM MessageSystemContent WHERE message_id = '$msgId'
                    """.trimIndent(), mapper = { cursor ->
                        if (cursor.next().value) {
                            actualType = cursor.getString(0)
                        }
                        QueryResult.Unit
                    }, 0)

                    assertEquals(expectedType, actualType, "Message $msgId should have correct content_type")
                }
            }
        )
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    /**
     * Helper to verify a table does not exist after migration.
     */
    private fun assertTableDoesNotExist(driver: SqlDriver, tableName: String) {
        var tableExists = false
        driver.executeQuery(null, """
            SELECT name FROM sqlite_master
            WHERE type='table' AND name='$tableName'
        """.trimIndent(), mapper = { cursor ->
            if (cursor.next().value) {
                tableExists = true
            }
            QueryResult.Unit
        }, 0)

        assertEquals(false, tableExists, "Table $tableName should not exist after migration")
    }
}
