/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.protection.JvmStorageKeyStore
import com.wire.kalium.persistence.protection.loadProtectedFileKey
import com.wire.kalium.persistence.protection.withProtectedFileLock
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.HexFormat

private val sqliteHeader = "SQLite format 3\u0000".encodeToByteArray()

// SQLite ignores unrecognized PRAGMAs. Verify the selected native engine before allowing disk writes.
private val hasNativeCipher by lazy {
    SQLiteDataSource().connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA cipher_version") && statement.resultSet.use { it.next() && !it.getString(1).isNullOrBlank() }
        }
    }
}

internal fun protectedDatabaseDriver(
    file: Path,
    schema: SqlSchema<QueryResult.Value<Unit>>,
    keyStore: JvmStorageKeyStore,
    enableWAL: Boolean,
): SqlDriver {
    check(hasNativeCipher) { "Protected JVM databases require a SQLCipher-enabled Xerial native library" }
    val path = file.toAbsolutePath().normalize()
    return withProtectedFileLock(path) {
        val missing = Files.notExists(path, NOFOLLOW_LINKS)
        if (missing) {
            val recoveryFiles = listOf("-wal", "-shm", "-journal").map { path.resolveSibling("${path.fileName}$it") }
            val hasNoRecoveryFiles = recoveryFiles.all { Files.notExists(it, NOFOLLOW_LINKS) }
            check(hasNoRecoveryFiles) { "Missing protected database with surviving recovery files" }
        } else {
            check(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Protected database must be a regular file" }
            check(Files.newInputStream(path).use { !it.readNBytes(sqliteHeader.size).contentEquals(sqliteHeader) }) {
                "Existing plaintext database requires a separate migration"
            }
        }

        val key = loadProtectedFileKey(keyStore, path, allowCreation = missing)
        try {
            val url = "jdbc:sqlite:${path.toUri().toASCIIString()}"
            if (!missing) verifyExistingDatabase(url, key)
            val config = cipherConfig(key).apply {
                setJournalMode(if (enableWAL) SQLiteConfig.JournalMode.WAL else SQLiteConfig.JournalMode.DELETE)
                enforceForeignKeys(true)
            }
            JdbcSqliteDriver(url = url, properties = config.toProperties(), schema = schema)
        } finally {
            key.fill(0)
        }
    }
}

private fun cipherConfig(key: ByteArray): SQLiteConfig =
    SQLiteConfig().apply {
        setHexKeyMode(SQLiteConfig.HexKeyMode.SQLCIPHER)
        setPragma(SQLiteConfig.Pragma.PASSWORD, HexFormat.of().formatHex(key))
        setTempStore(SQLiteConfig.TempStore.MEMORY)
    }

private fun verifyExistingDatabase(url: String, key: ByteArray) {
    val readOnly = cipherConfig(key).apply { setReadOnly(true) }
    try {
        DriverManager.getConnection(url, readOnly.toProperties()).use { connection ->
            connection.createStatement().use { statement ->
                // Preparing this query reads an authenticated page before SQLDelight can run migrations.
                statement.executeQuery("SELECT count(*) FROM sqlite_schema").close()
            }
        }
    } catch (_: SQLException) {
        error("Could not authenticate the existing protected JVM database")
    }
}
