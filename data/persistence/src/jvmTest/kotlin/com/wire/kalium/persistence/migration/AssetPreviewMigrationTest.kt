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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetPreviewMigrationTest {
    @Test
    fun existingVideoSurvivesPreviewMigrationAndPreviewSurvivesReopen() = runTest {
        val database = File("build/user-schema-dumps/asset-preview-upgrade.db")
        database.parentFile.mkdirs()
        File("src/commonMain/db_user/schemas/2.db").copyTo(database, overwrite = true)
        try {
            JdbcSqliteDriver("jdbc:sqlite:${database.absolutePath}").use { driver ->
                object : SuspendingTransacterImpl(driver) {}.transaction {
                    UserDatabase.Schema.awaitMigrate(driver, 2, 159)
                }
                driver.execute(null, """
                    INSERT INTO MessageAssetContent(
                        message_id, conversation_id, asset_size, asset_mime_type, asset_otr_key, asset_sha256, asset_id
                    )
                    VALUES ('video-message', 'conversation@domain', 100000, 'video/mp4', x'01', x'02', 'video-id')
                """.trimIndent(), 0)
                UserDatabase.Schema.awaitMigrate(driver, 159, UserDatabase.Schema.version)
                driver.executeQuery(
                    null, "SELECT assetPreview, assetPreviewId, assetPreviewDomain FROM MessageDetailsView LIMIT 0", { QueryResult.Unit }, 0
                )
                driver.execute(null, """
                    UPDATE MessageAssetContent
                    SET asset_preview = x'0304', preview_asset_id = 'preview-id', preview_asset_domain = 'preview.domain'
                """.trimIndent(), 0)
            }
            JdbcSqliteDriver("jdbc:sqlite:${database.absolutePath}").use { driver ->
                val sql = "SELECT asset_id, asset_size, preview_asset_id, hex(asset_preview) FROM MessageAssetContent"
                driver.executeQuery(null, sql, { cursor ->
                    cursor.next()
                    assertEquals("video-id", cursor.getString(0))
                    assertEquals(100000L, cursor.getLong(1))
                    assertEquals("preview-id", cursor.getString(2))
                    assertEquals("0304", cursor.getString(3))
                    QueryResult.Unit
                }, 0)
            }
        } finally {
            database.delete()
        }
    }
}
