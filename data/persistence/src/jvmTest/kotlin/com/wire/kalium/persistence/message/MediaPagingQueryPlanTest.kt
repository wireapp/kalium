/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-only */
package com.wire.kalium.persistence.message

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPagingQueryPlanTest {
    @Test
    fun mediaQueriesUseConversationIndexInsteadOfScanningMessages() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            UserDatabase.Schema.create(driver).await()
            val source = File("src/commonMain/db_user/com/wire/kalium/persistence/MessageAssetView.sq").readText()
            listOf("countMediaMessagesByConversationId", "getMediaMessagesByConversationId").forEach { name ->
                val query = source.substringAfter("$name:").substringBefore(';')
                    .replace(":conversationId", "'conversation@domain'")
                    .replace(":limit", "20")
                    .replace(":offset", "0")
                val plan = mutableListOf<String>()
                driver.executeQuery(null, "EXPLAIN QUERY PLAN $query", { cursor ->
                    while (cursor.next().value) plan += cursor.getString(3)!!
                    QueryResult.Unit
                }, 0)
                assertTrue(plan.any { it.contains("SEARCH Message USING INDEX") && it.contains("conversation_id=?") }, plan.toString())
                assertFalse(plan.any { Regex("SCAN (TABLE )?Message( |$)").containsMatchIn(it) }, plan.toString())
            }
        } finally {
            driver.close()
        }
    }
}
