/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.DatabaseManager

/** Owned and closed by the main native driver, independently of account write admission. */
internal interface SnapshotSqlDriver {
    val snapshotDriver: SqlDriver?
}

/**
 * SQLDelight normally reserves its writer connection for every transaction. A separate pool of
 * query-only connections lets timeline pages use consistent WAL snapshots during writer handoff.
 * Deferred BEGIN acquires no writer lock; SQLite enforces that these connections cannot write.
 */
internal class ReadSnapshotDatabaseManager(private val delegate: DatabaseManager) : DatabaseManager by delegate {
    override fun createMultiThreadedConnection(): DatabaseConnection = delegate.createMultiThreadedConnection().readOnly()
    override fun createSingleThreadedConnection(): DatabaseConnection = delegate.createSingleThreadedConnection().readOnly()

    private fun DatabaseConnection.readOnly(): DatabaseConnection {
        rawExecSql("PRAGMA query_only = 1")
        val connection = this
        return object : DatabaseConnection by connection {
            private var successful = false

            override fun beginTransaction() {
                connection.rawExecSql("BEGIN DEFERRED TRANSACTION")
                successful = false
            }

            override fun setTransactionSuccessful() {
                successful = true
            }

            override fun endTransaction() {
                connection.rawExecSql(if (successful) "COMMIT" else "ROLLBACK")
                successful = false
            }
        }
    }
}
