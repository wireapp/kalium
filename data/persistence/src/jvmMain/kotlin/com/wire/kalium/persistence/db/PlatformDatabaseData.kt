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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import org.sqlite.SQLiteConfig
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.File

actual data class PlatformDatabaseData(
    val storageData: StorageData,
    internal val owner: UserIDEntity? = null
)

sealed interface StorageData {
    data class FileBacked(val file: File) : StorageData
    data object InMemory : StorageData
}

/**
 * Creates a JVM SQLite driver with optional SQLDelight-managed schema initialization.
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
    return configuredDatabaseDriver(uri, schema, null, config)
}

internal fun configuredDatabaseDriver(
    uri: String,
    schema: SqlSchema<QueryResult.Value<Unit>>? = null,
    secret: ByteArray?,
    config: DriverConfigurationBuilder.() -> Unit
): SqlDriver {
    val driverConfiguration = DriverConfigurationBuilder().apply(config)
    val sqliteConfig = if (secret == null) {
        SQLiteConfig()
    } else {
        desktopCipherConfig(secret)
    }
    val journalMode = if (driverConfiguration.isWALEnabled) SQLiteConfig.JournalMode.WAL else SQLiteConfig.JournalMode.DELETE
    sqliteConfig.setJournalMode(journalMode)
    sqliteConfig.enforceForeignKeys(driverConfiguration.areForeignKeyConstraintsEnforced)
    val properties = sqliteConfig.toProperties()
    return if (schema == null) {
        JdbcSqliteDriver(uri, properties)
    } else {
        JdbcSqliteDriver(
            url = uri,
            properties = properties,
            schema = schema
        )
    }
}

// SQLite silently ignores unknown pragmas. Check the native engine before supplying any disk path.
private val nativeCipherAvailable by lazy {
    java.sql.DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT sqlite3mc_config('sqlcipher', 'hmac_use')").use { it.next() && it.getInt(1) == 1 }
        }
    }
}

internal fun desktopCipherConfig(secret: ByteArray): SQLiteConfig {
    check(nativeCipherAvailable) { "A native SQLite3MC engine with SQLCipher page authentication is required" }
    // Kalium supplies SQLCipher raw keys as ASCII x'<64 hex digits>'.
    val encoded = secret.decodeToString()
    require(Regex("x'[0-9a-fA-F]{64}'").matches(encoded)) { "Invalid SQLCipher raw key" }
    val rawKey = java.util.HexFormat.of().parseHex(encoded, 2, encoded.lastIndex)
    return try {
        SQLiteMCSqlCipherConfig.getV4Defaults().withRawUnsaltedKey(rawKey).build().apply {
            setTempStore(SQLiteConfig.TempStore.MEMORY)
        }
    } finally {
        rawKey.fill(0)
    }
}
