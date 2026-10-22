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
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.db.databaseDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetPreviewMigrationTest {
    @Test
    fun developDatabaseUpgradesAndReopensWithoutChangingExistingAssets() = runTest {
        withVersion161 { file, driver ->
            insertVideo(driver, "ready")
            assertFalse(driver.columns().contains("preview_asset_id"))
            driver.close()
            repeat(2) {
                openProduction(file).use { reopened ->
                    assertEquals(listOf(UserDatabase.Schema.version.toString()), reopened.values("PRAGMA user_version"))
                    assertEquals(listOf("video-ready", "100000", "0102", "0304", null), reopened.row("""
                        SELECT asset_id, asset_size, hex(asset_otr_key), hex(asset_sha256), preview_asset_id
                        FROM MessageAssetContent WHERE message_id = 'ready'
                    """.trimIndent(), 5))
                    assertEquals(listOf(null), reopened.values("SELECT assetPreviewId FROM MessageDetailsView WHERE id = 'ready'"))
                    assertFalse(reopened.columns().contains("asset_preview"))
                    assertEquals(emptyList(), reopened.values("PRAGMA foreign_key_check"))
                }
            }
        }
    }

    @Test
    fun productionFreshSchemaHasTypedPreviewColumnsAndNoEnvelope() {
        val file = File.createTempFile("asset-preview-fresh-", ".db")
        try {
            openProduction(file).use { driver ->
                assertFalse(driver.columns().contains("asset_preview"))
                assertTrue(driver.columns().containsAll(listOf("preview_asset_mime_type", "preview_asset_size", "preview_asset_otr_key")))
            }
        } finally {
            file.delete()
        }
    }

    private fun openProduction(file: File): SqlDriver = databaseDriver(
        uri = "jdbc:sqlite:${file.absolutePath}", schema = UserDatabase.Schema.synchronous(),
    ) { areForeignKeyConstraintsEnforced = true }

    private suspend fun withVersion161(block: suspend (File, SqlDriver) -> Unit) {
        val file = File.createTempFile("asset-preview-upgrade-", ".db")
        File("src/commonMain/db_user/schemas/2.db").copyTo(file, overwrite = true)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
                object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 2, 161) }
                driver.execute(null, "PRAGMA user_version = 161", 0)
                driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('sender@domain')", 0)
                driver.execute(null, """
                    INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id,
                        last_modified_date, access_list, access_role_list, mls_cipher_suite)
                    VALUES ('conversation@domain', 'GROUP', 'ESTABLISHED', 'PROTEUS', 'sender', 0, '', '', 'DEFAULT')
                """.trimIndent(), 0)
                block(file, driver)
            }
        } finally {
            file.delete()
        }
    }

    private fun insertVideo(driver: SqlDriver, id: String) {
        driver.execute(null, """
            INSERT INTO Message(id, conversation_id, content_type, creation_date, sender_user_id, status, visibility)
            VALUES (?, 'conversation@domain', 'ASSET', 1, 'sender@domain', 'SENT', 'VISIBLE')
        """.trimIndent(), 1) { bindString(0, id) }
        driver.execute(null, """
            INSERT INTO MessageAssetContent(message_id, conversation_id, asset_size, asset_mime_type,
                asset_otr_key, asset_sha256, asset_id)
            VALUES (?, 'conversation@domain', 100000, 'video/mp4', x'0102', x'0304', ?)
        """.trimIndent(), 2) {
            bindString(0, id)
            bindString(1, "video-$id")
        }
    }

    private fun SqlDriver.columns(): List<String?> = executeQuery(null, "PRAGMA table_info(MessageAssetContent)", { cursor ->
        val values = mutableListOf<String?>()
        while (cursor.next().value) values += cursor.getString(1)
        QueryResult.Value(values)
    }, 0).value

    private fun SqlDriver.values(sql: String): List<String?> = executeQuery(null, sql, { cursor ->
        val values = mutableListOf<String?>()
        while (cursor.next().value) values += cursor.getString(0)
        QueryResult.Value(values)
    }, 0).value

    private fun SqlDriver.row(sql: String, columns: Int): List<String?> = executeQuery(null, sql, { cursor ->
        check(cursor.next().value)
        QueryResult.Value((0 until columns).map(cursor::getString))
    }, 0).value
}
