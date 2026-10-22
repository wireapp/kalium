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

package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.db.QueryResult
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetMessageUpdateQueryTest : BaseDatabaseTest() {
    @Test
    fun continuationLookupUsesBothCompositePrimaryKeysWithoutScanning() {
        val self = newUserEntity("asset-query-self")
        val db = createDatabase(self.id, null, false)
        try {
            // Explain the production query so a later view/scan regression is caught.
            val sql = File("src/commonMain/db_user/com/wire/kalium/persistence/Messages.sq").readText()
                .substringAfter("selectAssetMessageUpdateState:").substringBefore(';')
                .replace(":messageId", "?").replace(":conversationId", "?")
            val plan = db.sqlDriver.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { cursor ->
                val details = mutableListOf<String>()
                while (cursor.next().value) details += cursor.getString(3)!!
                QueryResult.Value(details)
            }, 2) {
                bindString(0, "message")
                bindString(1, "conversation@domain")
            }.value
            assertEquals(2, plan.size, plan.toString())
            assertTrue(plan.all { it.startsWith("SEARCH ") && it.contains("USING INDEX sqlite_autoindex_") }, plan.toString())
            assertTrue(plan.all {
                it.contains("message_id=? AND conversation_id=?") || it.contains("id=? AND conversation_id=?")
            }, plan.toString())
        } finally {
            db.sqlDriver.close()
            deleteDatabase(self.id)
        }
    }
}
