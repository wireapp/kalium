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
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ForeignKeyMigrationRegressionTest {

    @Test
    fun givenOldestUserSnapshot_whenRunningEveryMigrationWithForeignKeysEnabled_thenHasNoViolations() = runTest {
        val (driver, databaseFile) = userDriverFromSchema(version = 2, enforceForeignKeys = true)

        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(
                    driver,
                    oldVersion = 2,
                    newVersion = UserDatabase.Schema.version,
                )
                assertEquals(emptyList(), driver.foreignKeyViolations())
            }

            assertEquals(1L, driver.singleLong("PRAGMA foreign_keys"))
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    @Test
    fun givenPopulatedHistoricalGlobalVersion2Schema_whenRunningEveryMigration_thenPreservesData() = runTest {
        val (driver, databaseFile) = globalDriverFromHistoricalVersion2Schema()

        try {
            driver.insertGlobalVersion2Fixture()

            object : SuspendingTransacterImpl(driver) {}.transaction {
                GlobalDatabase.Schema.awaitMigrate(
                    driver,
                    oldVersion = 2,
                    newVersion = GlobalDatabase.Schema.version,
                )
                assertEquals(emptyList(), driver.foreignKeyViolations())
            }

            assertEquals("proxy.example.com", driver.singleString("SELECT apiProxyHost FROM ServerConfiguration"))
            assertEquals(1L, driver.singleLong("SELECT apiProxyNeedsAuthentication FROM ServerConfiguration"))
            assertEquals(8080L, driver.singleLong("SELECT apiProxyPort FROM ServerConfiguration"))
            assertEquals(2L, driver.singleLong("SELECT commonApiVersion FROM ServerConfiguration"))
            assertEquals("user@example.com", driver.singleString("SELECT user_id FROM CurrentAccount"))
            assertEquals(0L, driver.singleLong("SELECT isPersistentWebSocketEnabled FROM Accounts"))
            assertEquals(1L, driver.singleLong("PRAGMA foreign_keys"))
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    @Test
    fun givenConversationWithAllDependents_whenRunningMigration24WithForeignKeysEnabled_thenPreservesEveryRow() = runTest {
        val (driver, databaseFile) = userDriverFromSchema(version = 2, enforceForeignKeys = true)

        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, oldVersion = 2, newVersion = 24)
            }
            driver.insertMigration24Fixture()
            assertEquals(emptyList(), driver.foreignKeyViolations())

            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, oldVersion = 24, newVersion = 25)
                assertEquals(emptyList(), driver.foreignKeyViolations())
            }

            val dependentTables = setOf(
                "Member",
                "Message",
                "MessageAssetContent",
                "MessageConversationChangedContent",
                "MessageFailedToDecryptContent",
                "MessageMemberChangeContent",
                "MessageMention",
                "MessageMissedCallContent",
                "MessageRestrictedAssetContent",
                "MessageTextContent",
                "MessageUnknownContent",
                "Reaction",
                "Receipt",
            )
            assertEquals(
                dependentTables.associateWith { 1L },
                dependentTables.associateWith { driver.rowCount(it) }
            )
            assertEquals(1_704_067_200_000L, driver.singleLong("SELECT last_modified_date FROM Conversation"))
            assertEquals(1_704_067_200_000L, driver.singleLong("SELECT creation_date FROM Message"))
            assertEquals(1L, driver.singleLong("PRAGMA foreign_keys"))
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    @Test
    fun givenReferencedMalformedIds_whenRunningMigration33WithDeferredForeignKeys_thenRepairsEveryReference() = runTest {
        val (driver, databaseFile) = userDriverFromSchema(version = 34, enforceForeignKeys = true)

        try {
            driver.insertMalformedQualifiedIdFixture()

            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, oldVersion = 33, newVersion = 34)
                assertEquals(emptyList(), driver.foreignKeyViolations())
                assertEquals(0L, driver.singleLong("PRAGMA defer_foreign_keys"))
                assertFailsWith<SQLiteException> {
                    driver.execute(
                        null,
                        "INSERT INTO Client(user_id, id) VALUES ('missing@example.com', 'invalid-client')",
                        0
                    )
                }
                UserDatabase.Schema.awaitMigrate(
                    driver,
                    oldVersion = 34,
                    newVersion = UserDatabase.Schema.version,
                )
            }

            assertEquals(
                mapOf(
                    "Client" to "user@example.com",
                    "Member" to "user@example.com|conversation@example.com",
                    "Message" to "user@example.com|conversation@example.com",
                    "Reaction" to "user@example.com|conversation@example.com",
                    "Receipt" to "user@example.com|conversation@example.com",
                    "UnreadEvent" to "conversation@example.com",
                ),
                mapOf(
                    "Client" to driver.singleString("SELECT user_id FROM Client"),
                    "Member" to driver.singleString("SELECT user || '|' || conversation FROM Member"),
                    "Message" to driver.singleString(
                        "SELECT sender_user_id || '|' || conversation_id FROM Message"
                    ),
                    "Reaction" to driver.singleString(
                        "SELECT sender_id || '|' || conversation_id FROM Reaction"
                    ),
                    "Receipt" to driver.singleString(
                        "SELECT user_id || '|' || conversation_id FROM Receipt"
                    ),
                    "UnreadEvent" to driver.singleString("SELECT conversation_id FROM UnreadEvent"),
                )
            )
            assertEquals(1L, driver.singleLong("PRAGMA foreign_keys"))
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    @Test
    fun givenReferencedMalformedIds_whenRunningMigration34WithForeignKeysEnabled_thenDeletesEveryReference() = runTest {
        val (driver, databaseFile) = userDriverFromSchema(version = 34, enforceForeignKeys = true)

        try {
            driver.insertMalformedQualifiedIdFixture()

            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, oldVersion = 34, newVersion = 35)
                assertEquals(emptyList(), driver.foreignKeyViolations())
            }

            assertEquals(
                setOf(
                    "Client",
                    "Conversation",
                    "Member",
                    "Message",
                    "Reaction",
                    "Receipt",
                    "UnreadEvent",
                    "User",
                ).associateWith { 0L },
                setOf(
                    "Client",
                    "Conversation",
                    "Member",
                    "Message",
                    "Reaction",
                    "Receipt",
                    "UnreadEvent",
                    "User",
                ).associateWith { driver.rowCount(it) }
            )
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    @Test
    fun givenSelectedAccount_whenRunningGlobalMigration4WithForeignKeysEnabled_thenKeepsSelection() = runTest {
        val databaseFile = copySchema("db_global", version = 5)
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties())

        try {
            driver.execute(
                null,
                """
                    INSERT INTO Accounts(
                        id,
                        server_config_id,
                        isPersistentWebSocketEnabled
                    ) VALUES (
                        'user@example.com',
                        'server',
                        1
                    )
                """.trimIndent(),
                0
            )
            driver.execute(
                null,
                "INSERT INTO CurrentAccount(id, user_id) VALUES (0, 'user@example.com')",
                0
            )

            object : SuspendingTransacterImpl(driver) {}.transaction {
                GlobalDatabase.Schema.awaitMigrate(driver, oldVersion = 4, newVersion = 5)
                assertEquals(emptyList(), driver.foreignKeyViolations())
            }

            assertEquals("user@example.com", driver.singleString("SELECT user_id FROM CurrentAccount"))
            assertEquals(1L, driver.singleLong("PRAGMA foreign_keys"))
            assertEquals(emptyList(), driver.foreignKeyViolations())
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    private fun userDriverFromSchema(
        version: Int,
        enforceForeignKeys: Boolean,
    ): Pair<JdbcSqliteDriver, File> {
        val databaseFile = copySchema("db_user", version)
        val config = SQLiteConfig().apply { enforceForeignKeys(enforceForeignKeys) }
        return JdbcSqliteDriver(
            "jdbc:sqlite:${databaseFile.absolutePath}",
            config.toProperties()
        ) to databaseFile
    }

    /**
     * Reproduces the checked-in global version-2 schema from before migration 2 was introduced.
     * Keeping the historical DDL here lets this fixture carry real rows without mutating the
     * repository's immutable schema snapshot.
     */
    private fun globalDriverFromHistoricalVersion2Schema(): Pair<JdbcSqliteDriver, File> {
        val databaseFile = Files.createTempFile("db_global-migration-2-", ".db").toFile()
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties())

        driver.execute(
            null,
            """
                CREATE TABLE Accounts (
                    id TEXT PRIMARY KEY NOT NULL,
                    scim_external_id TEXT,
                    subject TEXT,
                    tenant TEXT,
                    server_config_id TEXT NOT NULL,
                    logout_reason TEXT
                )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
                CREATE TABLE CurrentAccount (
                    id INTEGER PRIMARY KEY NOT NULL,
                    user_id TEXT,
                    FOREIGN KEY(user_id) REFERENCES Accounts(id) ON DELETE CASCADE
                )
            """.trimIndent(),
            0
        )
        driver.execute(
            null,
            """
                CREATE TABLE ServerConfiguration (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    apiBaseUrl TEXT NOT NULL,
                    accountBaseUrl TEXT NOT NULL,
                    webSocketBaseUrl TEXT NOT NULL,
                    blackListUrl TEXT NOT NULL,
                    teamsUrl TEXT NOT NULL,
                    websiteUrl TEXT NOT NULL,
                    isOnPremises INTEGER NOT NULL,
                    domain TEXT UNIQUE,
                    commonApiVersion INTEGER NOT NULL,
                    federation INTEGER NOT NULL,
                    proxyApi TEXT,
                    proxyNeedsAuthentication INTEGER,
                    proxyPort INTEGER,
                    CONSTRAINT server_config_unique UNIQUE (title, apiBaseUrl, webSocketBaseUrl)
                )
            """.trimIndent(),
            0
        )
        driver.execute(null, "PRAGMA user_version = 2", 0)

        return driver to databaseFile
    }

    private fun copySchema(database: String, version: Int): File {
        val source = File("src/commonMain/$database/schemas/$version.db")
        val target = Files.createTempFile("$database-migration-$version-", ".db").toFile()
        source.copyTo(target, overwrite = true)
        return target
    }

    private fun JdbcSqliteDriver.insertMalformedQualifiedIdFixture() {
        execute(null, "INSERT INTO SelfUser(id) VALUES ('self@example.com')", 0)
        execute(null, "INSERT INTO User(qualified_id) VALUES ('user@')", 0)
        execute(
            null,
            """
                INSERT INTO Conversation(
                    qualified_id,
                    type,
                    mls_group_state,
                    protocol,
                    creator_id,
                    last_modified_date,
                    access_list,
                    access_role_list,
                    mls_cipher_suite
                ) VALUES (
                    'conversation@',
                    'GROUP',
                    'PENDING_JOIN',
                    'PROTEUS',
                    'creator',
                    0,
                    '[]',
                    '[]',
                    'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Message(
                    id,
                    content_type,
                    conversation_id,
                    creation_date,
                    sender_user_id,
                    status,
                    visibility
                ) VALUES (
                    'message',
                    'TEXT',
                    'conversation@',
                    0,
                    'user@',
                    'SENT',
                    'VISIBLE'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            "INSERT INTO Member(user, conversation, role) VALUES ('user@', 'conversation@', 'wire_member')",
            0
        )
        execute(null, "INSERT INTO Client(user_id, id) VALUES ('user@', 'client')", 0)
        execute(
            null,
            """
                INSERT INTO Receipt(message_id, conversation_id, user_id, type, date)
                VALUES ('message', 'conversation@', 'user@', 'READ', '0')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Reaction(message_id, conversation_id, sender_id, emoji, date)
                VALUES ('message', 'conversation@', 'user@', '👍', '0')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO UnreadEvent(id, type, conversation_id, creation_date)
                VALUES ('message', 'MESSAGE', 'conversation@', 0)
            """.trimIndent(),
            0
        )
    }

    private fun JdbcSqliteDriver.insertGlobalVersion2Fixture() {
        execute(
            null,
            """
                INSERT INTO ServerConfiguration(
                    id,
                    title,
                    apiBaseUrl,
                    accountBaseUrl,
                    webSocketBaseUrl,
                    blackListUrl,
                    teamsUrl,
                    websiteUrl,
                    isOnPremises,
                    domain,
                    commonApiVersion,
                    federation,
                    proxyApi,
                    proxyNeedsAuthentication,
                    proxyPort
                ) VALUES (
                    'server',
                    'Server',
                    'https://api.example.com',
                    'https://account.example.com',
                    'https://websocket.example.com',
                    'https://blacklist.example.com',
                    'https://teams.example.com',
                    'https://www.example.com',
                    1,
                    'example.com',
                    3,
                    1,
                    'proxy.example.com',
                    1,
                    8080
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Accounts(id, scim_external_id, subject, tenant, server_config_id, logout_reason)
                VALUES ('user@example.com', 'scim-id', 'subject', 'tenant', 'server', NULL)
            """.trimIndent(),
            0
        )
        execute(
            null,
            "INSERT INTO CurrentAccount(id, user_id) VALUES (0, 'user@example.com')",
            0
        )
    }

    private fun JdbcSqliteDriver.insertMigration24Fixture() {
        execute(null, "INSERT INTO User(qualified_id) VALUES ('user@example.com')", 0)
        execute(
            null,
            """
                INSERT INTO Conversation(
                    qualified_id,
                    type,
                    mls_group_state,
                    protocol,
                    creator_id,
                    last_modified_date,
                    access_list,
                    access_role_list,
                    mls_cipher_suite
                ) VALUES (
                    'conversation@example.com',
                    'GROUP',
                    'PENDING_JOIN',
                    'PROTEUS',
                    'creator',
                    '2024-01-01T00:00:00.000Z',
                    '[]',
                    '[]',
                    'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Message(
                    id,
                    content_type,
                    conversation_id,
                    date,
                    sender_user_id,
                    status,
                    visibility
                ) VALUES (
                    'message',
                    'TEXT',
                    'conversation@example.com',
                    '2024-01-01T00:00:00.000Z',
                    'user@example.com',
                    'SENT',
                    'VISIBLE'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Member(user, conversation, role)
                VALUES ('user@example.com', 'conversation@example.com', 'wire_member')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageAssetContent(
                    message_id,
                    conversation_id,
                    asset_size,
                    asset_mime_type,
                    asset_otr_key,
                    asset_sha256,
                    asset_id
                ) VALUES (
                    'message',
                    'conversation@example.com',
                    1,
                    'image/png',
                    X'01',
                    X'02',
                    'asset'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageConversationChangedContent(message_id, conversation_id, conversation_name)
                VALUES ('message', 'conversation@example.com', 'name')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageFailedToDecryptContent(message_id, conversation_id)
                VALUES ('message', 'conversation@example.com')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageMemberChangeContent(
                    message_id,
                    conversation_id,
                    member_change_list,
                    member_change_type
                ) VALUES (
                    'message',
                    'conversation@example.com',
                    '[]',
                    'ADDED'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageMention(message_id, conversation_id, start, length, user_id)
                VALUES ('message', 'conversation@example.com', 0, 1, 'user@example.com')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageMissedCallContent(message_id, conversation_id, caller_id)
                VALUES ('message', 'conversation@example.com', 'user@example.com')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageRestrictedAssetContent(
                    message_id,
                    conversation_id,
                    asset_mime_type,
                    asset_size,
                    asset_name
                ) VALUES (
                    'message',
                    'conversation@example.com',
                    'image/png',
                    1,
                    'asset'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageTextContent(message_id, conversation_id, text_body)
                VALUES ('message', 'conversation@example.com', 'text')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageUnknownContent(message_id, conversation_id)
                VALUES ('message', 'conversation@example.com')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Reaction(message_id, conversation_id, sender_id, emoji, date)
                VALUES ('message', 'conversation@example.com', 'user@example.com', '👍', '0')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Receipt(message_id, conversation_id, user_id, type, date)
                VALUES ('message', 'conversation@example.com', 'user@example.com', 'READ', '0')
            """.trimIndent(),
            0
        )
    }

    private fun JdbcSqliteDriver.foreignKeyViolations(): List<String> {
        val result = mutableListOf<String>()
        executeQuery(
            null,
            "PRAGMA foreign_key_check",
            mapper = { cursor ->
                while (cursor.next().value) {
                    result += listOf(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                    ).joinToString("|")
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }

    private fun JdbcSqliteDriver.singleString(sql: String): String? {
        var result: String? = null
        executeQuery(
            null,
            sql,
            mapper = { cursor ->
                if (cursor.next().value) {
                    result = cursor.getString(0)
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }

    private fun JdbcSqliteDriver.singleLong(sql: String): Long? {
        var result: Long? = null
        executeQuery(
            null,
            sql,
            mapper = { cursor ->
                if (cursor.next().value) {
                    result = cursor.getLong(0)
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }

    private fun JdbcSqliteDriver.rowCount(table: String): Long =
        requireNotNull(singleLong("SELECT COUNT(*) FROM $table"))
}
