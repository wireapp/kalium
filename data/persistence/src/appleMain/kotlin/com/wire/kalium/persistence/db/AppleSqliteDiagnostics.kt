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
package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize

internal object AppleSqliteDiagnostics {
    private val logger = kaliumLogger.withTextTag("AppleSqliteDiagnostics")
    private const val WAL_PROBE_INTERVAL_MS = 15_000L

    fun start(scope: CoroutineScope, sqlDriver: SqlDriver, label: String, dbPath: String?) {
        logStartup(sqlDriver, label, dbPath)
        scope.launch {
            while (isActive) {
                delay(WAL_PROBE_INTERVAL_MS)
                logWalProbe(sqlDriver, label, dbPath)
            }
        }
    }

    private fun logStartup(sqlDriver: SqlDriver, label: String, dbPath: String?) {
        runCatching {
            val journalMode = querySingleString(sqlDriver, "PRAGMA journal_mode")
            val busyTimeout = querySingleLong(sqlDriver, "PRAGMA busy_timeout")
            val walAutocheckpoint = querySingleLong(sqlDriver, "PRAGMA wal_autocheckpoint")
            val pageSize = querySingleLong(sqlDriver, "PRAGMA page_size")
            val pageCount = querySingleLong(sqlDriver, "PRAGMA page_count")
            val freelistCount = querySingleLong(sqlDriver, "PRAGMA freelist_count")
            logger.i(
                "[SqliteDiag] db=$label journal_mode=$journalMode busy_timeout=$busyTimeout " +
                    "wal_autocheckpoint=$walAutocheckpoint page_size=$pageSize page_count=$pageCount " +
                    "freelist_count=$freelistCount ${formatFileSizes(dbPath)}"
            )
        }.onFailure {
            logger.w("[SqliteDiag] db=$label startup probe failed error=${it::class.simpleName}")
        }
    }

    private fun logWalProbe(sqlDriver: SqlDriver, label: String, dbPath: String?) {
        runCatching {
            val result = sqlDriver.executeQuery(
                identifier = null,
                sql = "PRAGMA wal_checkpoint(PASSIVE)",
                mapper = { cursor -> QueryResult.Value(readWalCheckpoint(cursor)) },
                parameters = 0,
            ).value ?: return

            logger.i(
                "[WalDiag] db=$label busy=${result.busy} log=${result.log} checkpointed=${result.checkpointed} " +
                    formatFileSizes(dbPath)
            )
        }.onFailure {
            logger.w("[WalDiag] db=$label probe failed error=${it::class.simpleName}")
        }
    }

    private fun readWalCheckpoint(cursor: SqlCursor): WalCheckpointResult? {
        if (!cursor.next().value) return null
        return WalCheckpointResult(
            busy = cursor.getLong(0) ?: -1L,
            log = cursor.getLong(1) ?: -1L,
            checkpointed = cursor.getLong(2) ?: -1L,
        )
    }

    private fun querySingleString(sqlDriver: SqlDriver, sql: String): String? =
        sqlDriver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) cursor.getString(0) else null
                )
            },
            parameters = 0,
        ).value

    private fun querySingleLong(sqlDriver: SqlDriver, sql: String): Long? =
        sqlDriver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) cursor.getLong(0) else null
                )
            },
            parameters = 0,
        ).value

    private fun formatFileSizes(dbPath: String?): String {
        if (dbPath.isNullOrBlank()) return "dbBytes=-1 walBytes=-1 shmBytes=-1"
        return "dbBytes=${fileSize(dbPath)} walBytes=${fileSize("$dbPath-wal")} shmBytes=${fileSize("$dbPath-shm")}" 
    }

    private fun fileSize(path: String): Long {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return -1L
        val value = attributes[NSFileSize] as? Number ?: return -1L
        return value.toLong()
    }

    private data class WalCheckpointResult(
        val busy: Long,
        val log: Long,
        val checkpointed: Long,
    )
}
