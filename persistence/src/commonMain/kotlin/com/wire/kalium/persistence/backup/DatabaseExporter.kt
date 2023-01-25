/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver

interface DatabaseExporter {
    fun beforeBackup()
}

internal class DatabaseExporterImpl internal constructor(
    private val sqlDriver: SqlDriver
) : DatabaseExporter {

    /*
    https://www.sqlite.org/c3ref/c_checkpoint_full.html

    #define SQLITE_CHECKPOINT_PASSIVE  0  /* Do as much as possible w/o blocking */
    #define SQLITE_CHECKPOINT_FULL     1  /* Wait for writers, then checkpoint */
    #define SQLITE_CHECKPOINT_RESTART  2  /* Like FULL but wait for readers */
    #define SQLITE_CHECKPOINT_TRUNCATE 3  /* Like RESTART but also truncate WAL */
     */
    override fun beforeBackup() {
        sqlDriver.executeQuery(null, "PRAGMA wal_checkpoint(3)", {}, 0)
    }
}
