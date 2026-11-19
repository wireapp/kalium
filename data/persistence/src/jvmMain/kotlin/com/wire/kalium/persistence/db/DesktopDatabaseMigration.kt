/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.replaceDurably
import org.sqlite.SQLiteConfig
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager

private val sqliteHeader = "SQLite format 3\u0000".encodeToByteArray()

/** Missing and confirmed plaintext databases may acquire a new key. I/O failures propagate. */
fun desktopDatabaseNeedsExistingKey(path: Path): Boolean = try {
    check(!Files.isSymbolicLink(path)) { "Cannot open a symbolic link to a protected database" }
    Files.newInputStream(path).use { !it.readNBytes(sqliteHeader.size).contentEquals(sqliteHeader) }
} catch (_: NoSuchFileException) {
    for (suffix in listOf("-wal", "-shm", "-journal", ".encrypting")) {
        val companion = path.resolveSibling("${path.fileName}$suffix")
        check(Files.notExists(companion, LinkOption.NOFOLLOW_LINKS)) {
            "Missing database with surviving recovery files"
        }
    }
    false
}

/** Migrate an existing account without opening its online session or creating a missing database. */
fun migrateDesktopUserDatabase(path: Path, secret: UserDBSecret) = DesktopDatabaseMigration.prepare(path, secret.value)

internal object DesktopDatabaseMigration {
    @Synchronized
    fun prepare(path: Path, secret: ByteArray, beforeCutover: () -> Unit = {}) {
        val lock = path.resolveSibling("${path.fileName}.migration.lock")
        FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                if (desktopDatabaseNeedsExistingKey(path) || Files.notExists(path)) return
                migrate(path, secret, beforeCutover)
            }
        }
    }

    private fun migrate(source: Path, secret: ByteArray, beforeCutover: () -> Unit) {
        val target = source.resolveSibling("${source.fileName}.encrypting")
        val encoded = secret.decodeToString()
        val cipher = desktopCipherConfig(secret)
        val destination = target.toUri().toASCIIString() + "?cipher=sqlcipher&legacy=4&key=x%27" +
            encoded.substring(2, encoded.lastIndex) + "%27"
        val plain = SQLiteConfig().apply { setTempStore(SQLiteConfig.TempStore.MEMORY) }
        try {
            DriverManager.getConnection("jdbc:sqlite:$source", plain.toProperties()).use { input ->
                input.createStatement().use { statement ->
                    statement.execute("PRAGMA locking_mode=EXCLUSIVE")
                    check(input.scalar("PRAGMA journal_mode=DELETE") == "delete")
                    statement.execute("BEGIN EXCLUSIVE")
                    statement.execute("COMMIT")
                }
                check(input.scalar("PRAGMA integrity_check") == "ok") { "Source database integrity check failed" }
                discardStaging(target)
                input.prepareStatement("VACUUM INTO ?").use {
                    it.setString(1, destination)
                    it.execute()
                }
                check(desktopDatabaseNeedsExistingKey(target)) { "Migration produced an unencrypted database" }
                DriverManager.getConnection("jdbc:sqlite:$target", cipher.toProperties()).use { output ->
                    check(output.scalar("PRAGMA integrity_check") == "ok") { "Encrypted copy integrity check failed" }
                    check(input.scalar("PRAGMA user_version") == output.scalar("PRAGMA user_version"))
                    check(input.scalar("PRAGMA application_id") == output.scalar("PRAGMA application_id"))
                    verifyRows(input, output)
                }
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(source.resolveSibling("${source.fileName}$suffix"))) { "Source database is still in use" }
            }
            FileChannel.open(target, StandardOpenOption.WRITE).use { it.force(true) }
            beforeCutover()
            replaceDurably(target, source)
        } finally {
            discardStaging(target)
        }
    }

    private fun discardStaging(target: Path) {
        for (suffix in listOf("-wal", "-shm", "-journal", "")) {
            Files.deleteIfExists(target.resolveSibling("${target.fileName}$suffix"))
        }
    }

    /** Compare one row at a time so validation memory usage does not grow with the message history. */
    private fun verifyRows(input: Connection, output: Connection) {
        val tables = input.createStatement().use { statement ->
            statement.executeQuery("SELECT name, wr FROM pragma_table_list WHERE schema='main' AND type<>'view' AND name<>'sqlite_schema'").use {
                buildList { while (it.next()) add(it.getString(1) to (it.getInt(2) == 0)) }
            }
        }
        for ((name, hasRowId) in tables) {
            val columns = if (hasRowId) "rowid, *" else "*"
            val order = if (hasRowId) "rowid" else input.prepareStatement(
                "SELECT name FROM pragma_table_xinfo(?) WHERE pk > 0 ORDER BY pk"
            ).use { keys ->
                keys.setString(1, name)
                keys.executeQuery().use { fields -> buildList { while (fields.next()) add(quote(fields.getString(1))) }.joinToString() }
            }
            val sql = "SELECT $columns FROM ${quote(name)} ORDER BY $order"
            input.createStatement().use { from ->
                output.createStatement().use { to ->
                    from.executeQuery(sql).use { original ->
                        to.executeQuery(sql).use { copy ->
                            val columnCount = original.metaData.columnCount
                            while (original.next()) {
                                check(copy.next()) { "Encrypted copy is missing rows" }
                                for (column in 1..columnCount) {
                                    check(java.util.Objects.deepEquals(original.getObject(column), copy.getObject(column))) {
                                        "Encrypted migration changed a stored value"
                                    }
                                }
                            }
                            check(!copy.next()) { "Encrypted copy has extra rows" }
                        }
                    }
                }
            }
        }
    }

    private fun quote(identifier: String) = "\"" + identifier.replace("\"", "\"\"") + "\""

    private fun Connection.scalar(query: String): String = createStatement().use { statement ->
        statement.executeQuery(query).use { rows ->
            check(rows.next())
            rows.getString(1)
        }
    }
}
