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
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sqlite.SQLiteConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Migration143Test {

    @Test
    fun givenLegacyViewsAtVersions140To143_whenMigratingTo144_thenRepairsTheViewBeforeMigration145() = runTest {
        for (version in 140L..143L) {
            withLegacyPreviewDatabase(version) { driver ->
                driver.migrate(version, 144)

                assertPreviewPreserved(driver)

                driver.migrate(144, UserDatabase.Schema.version)
                assertPreviewPreserved(driver)
            }
        }
    }

    @Test
    fun givenLegacyViewAtVersion145_whenMigratingToCurrentVersion_thenRepairsTheView() = runTest {
        withLegacyPreviewDatabase(145) { driver ->
            driver.migrate(145, UserDatabase.Schema.version)

            assertPreviewPreserved(driver)
        }
    }

    private suspend fun withLegacyPreviewDatabase(version: Long, test: suspend (JdbcSqliteDriver) -> Unit) {
        val databaseFile = Files.createTempFile("migration-143-", ".db").toFile()
        File("src/commonMain/db_user/schemas/34.db").copyTo(databaseFile, overwrite = true)
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties())

        try {
            driver.migrate(34, version)
            driver.insertPreview()

            // Reproduce the view already stored by releases before the permanent URL fix.
            val viewSql = requireNotNull(driver.singleString("SELECT sql FROM sqlite_schema WHERE name = 'MessageDetailsView'"))
            val legacyViewSql = viewSql.replace(Regex("IFNULL\\(permanent_url,\\s*url\\)"), LEGACY_FALLBACK)
            assertTrue(legacyViewSql.contains(LEGACY_FALLBACK))
            driver.execute(null, "DROP VIEW MessageDetailsView", 0)
            driver.execute(null, legacyViewSql, 0)

            test(driver)
        } finally {
            driver.close()
            databaseFile.delete()
        }
    }

    private suspend fun JdbcSqliteDriver.migrate(oldVersion: Long, newVersion: Long) {
        object : SuspendingTransacterImpl(this) {}.transaction {
            UserDatabase.Schema.awaitMigrate(this@migrate, oldVersion, newVersion)
        }
    }

    private fun JdbcSqliteDriver.insertPreview() {
        execute(null, "INSERT INTO SelfUser(id) VALUES ('user@example.com')", 0)
        execute(null, "INSERT INTO User(qualified_id) VALUES ('user@example.com')", 0)
        execute(
            null,
            """
                INSERT INTO Conversation(
                    qualified_id, type, mls_group_state, protocol, creator_id,
                    last_modified_date, access_list, access_role_list, mls_cipher_suite
                ) VALUES (
                    'conversation@example.com', 'GROUP', 'PENDING_JOIN', 'PROTEUS', 'user',
                    0, '[]', '[]', 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'
                )
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO Message(id, content_type, conversation_id, creation_date, sender_user_id, status, visibility)
                VALUES ('message', 'TEXT', 'conversation@example.com', 0, 'user@example.com', 'SENT', 'VISIBLE')
            """.trimIndent(),
            0
        )
        execute(
            null,
            """
                INSERT INTO MessageLinkPreview(message_id, conversation_id, url, url_offset, permanent_url, title, summary)
                VALUES ('message', 'conversation@example.com', '$URL', 0, NULL, 'Title', 'Summary')
            """.trimIndent(),
            0
        )
    }

    private fun assertPreviewPreserved(driver: JdbcSqliteDriver) {
        val linkPreviews = requireNotNull(driver.singleString("SELECT linkPreviews FROM MessageDetailsView WHERE id = 'message'"))
        val preview = Json.parseToJsonElement(linkPreviews).jsonArray.single().jsonObject
        assertEquals(URL, preview.getValue("permanentUrl").jsonPrimitive.content)
        assertEquals("message", driver.singleString("SELECT id FROM Message"))
        assertEquals(URL, driver.singleString("SELECT url FROM MessageLinkPreview"))
        assertNull(driver.singleString("SELECT permanent_url FROM MessageLinkPreview"))
        assertEquals("ok", driver.singleString("PRAGMA integrity_check"))
        assertFalse(
            driver.executeQuery(null, "PRAGMA foreign_key_check", { cursor -> QueryResult.Value(cursor.next().value) }, 0).value
        )
    }

    private fun JdbcSqliteDriver.singleString(sql: String): String? =
        executeQuery(null, sql, { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) }, 0).value

    private companion object {
        const val URL = "https://wire.com/preview"
        const val LEGACY_FALLBACK = "IFNULL(permanent_url,\"' || url || '\")"
    }
}
