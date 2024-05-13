/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import java.io.File

actual data class PlatformDatabaseData(
    val storageData: StorageData
)

sealed interface StorageData {
    data class FileBacked(val file: File) : StorageData
    data object InMemory : StorageData
    data class RDBMS(val uri: String, val username: String, val password: String) : StorageData
}

fun databaseDriver(uri: String, config: DriverConfigurationBuilder.() -> Unit = {}): SqlDriver {
    val driverConfiguration = DriverConfigurationBuilder().apply(config)
    val sqliteConfig = SQLiteConfig()
    val journalMode = if (driverConfiguration.isWALEnabled) SQLiteConfig.JournalMode.WAL else SQLiteConfig.JournalMode.DELETE
    sqliteConfig.setJournalMode(journalMode)
    sqliteConfig.enforceForeignKeys(driverConfiguration.areForeignKeyConstraintsEnforced)
    return JdbcSqliteDriver(uri, sqliteConfig.toProperties())
}
