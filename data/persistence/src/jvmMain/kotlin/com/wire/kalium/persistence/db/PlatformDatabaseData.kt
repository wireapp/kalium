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

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import java.io.File
import java.util.Properties

// Another process, or a second SDK instance on the same file, waits this long for SQLite's lock
// instead of failing right away.
private const val BUSY_TIMEOUT_MILLIS = 5_000

actual data class PlatformDatabaseData(
    val storageData: StorageData
)

sealed interface StorageData {
    data class FileBacked(val file: File) : StorageData
    data object InMemory : StorageData
}

/**
 * Creates a JVM SQLite driver with optional SQLDelight-managed schema initialization.
 *
 * File-backed databases get a [PooledJdbcSqliteDriver], which keeps its connections open. In-memory
 * databases keep SQLDelight's single-connection driver: every new connection to them would be a new,
 * empty database.
 *
 * Behavior:
 * - When [schema] is provided: SQLDelight will create or migrate the database to [schema.version]
 *   and update `PRAGMA user_version`.
 * - When [schema] is null: a raw driver is returned and the caller is responsible for schema
 *   creation and/or migration.
 *
 * Use the raw mode only for special flows that intentionally control migration externally
 * (for example import/backup flows).
 */
fun databaseDriver(
    uri: String,
    schema: SqlSchema<QueryResult.Value<Unit>>? = null,
    config: DriverConfigurationBuilder.() -> Unit = {}
): SqlDriver {
    val properties = connectionProperties(DriverConfigurationBuilder().apply(config))
    return when {
        !isInMemory(uri) -> PooledJdbcSqliteDriver(uri, properties).also { driver ->
            schema?.let { driver.createOrMigrate(it) }
        }
        schema == null -> JdbcSqliteDriver(uri, properties)
        else -> JdbcSqliteDriver(url = uri, properties = properties, schema = schema)
    }
}

private fun connectionProperties(configuration: DriverConfigurationBuilder): Properties =
    SQLiteConfig().apply {
        setJournalMode(if (configuration.isWALEnabled) SQLiteConfig.JournalMode.WAL else SQLiteConfig.JournalMode.DELETE)
        enforceForeignKeys(configuration.areForeignKeyConstraintsEnforced)
        busyTimeout = BUSY_TIMEOUT_MILLIS
    }.toProperties()

// The rules SQLDelight's JdbcSqliteDriver uses to pick its single-connection mode.
private fun isInMemory(url: String): Boolean {
    val path = url.substringBefore('?').substringAfter("jdbc:sqlite:")
    return path.isEmpty() || path == ":memory:" || path == "file::memory:" || url.contains("mode=memory")
}

/** Creates or migrates [schema] based on `PRAGMA user_version`, like SQLDelight's `JdbcSqliteDriver(schema = ...)`. */
private fun SqlDriver.createOrMigrate(schema: SqlSchema<QueryResult.Value<Unit>>) {
    val driver = this
    object : TransacterImpl(driver) {}.transaction {
        val version = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            parameters = 0
        ).value ?: 0L
        if (version == 0L) {
            schema.create(driver).value
        } else if (version < schema.version) {
            schema.migrate(driver, version, schema.version).value
        }
        if (version < schema.version) {
            driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
        }
    }
}
