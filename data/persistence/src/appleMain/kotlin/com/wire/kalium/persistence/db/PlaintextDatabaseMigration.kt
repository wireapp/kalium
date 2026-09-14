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

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.getVersion
import co.touchlab.sqliter.withConnection
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.rename

/** The encrypted copy is written next to the database under this suffix, then renamed over it. */
internal const val ENCRYPTED_COPY_SUFFIX = ".encrypting"

/**
 * Encrypts an Apple database that an earlier version of Kalium stored unencrypted, before a driver opens it with [key].
 *
 * An unencrypted file starts with the SQLite header, an encrypted one with SQLCipher's random salt, so only an
 * unencrypted file is touched. It is exported with `sqlcipher_export` into an encrypted copy, which is renamed over it.
 * The export runs with a rollback journal, which checkpoints a WAL database first. Until the rename, the unencrypted
 * file stays intact, and a copy left behind by an interrupted run is deleted before the next one.
 *
 * Callers must make sure nothing else has the database open.
 */
internal fun encryptPlaintextDatabase(directory: String, name: String, key: String) {
    val path = "$directory/$name"
    if (!hasSqliteHeader(path)) return

    kaliumLogger.i("Encrypting a database that an earlier version stored unencrypted")
    val copyPath = "$path$ENCRYPTED_COPY_SUFFIX"
    deleteFiles(copyPath)
    val configuration = DatabaseConfiguration(
        name = name,
        version = NO_VERSION_CHECK,
        create = {},
        journalMode = JournalMode.DELETE,
        extendedConfig = DatabaseConfiguration.Extended(basePath = directory)
    )
    createDatabaseManager(configuration).withConnection { connection ->
        connection.requireSqlCipher()
        connection.rawExecSql("ATTACH DATABASE ${copyPath.toSqlLiteral()} AS $COPY KEY ${key.toSqlLiteral()}")
        try {
            connection.rawExecSql("SELECT sqlcipher_export('$COPY')")
            // sqlcipher_export copies the schema and the data, but not the schema version SQLDelight migrates from.
            connection.rawExecSql("PRAGMA $COPY.user_version = ${connection.getVersion()}")
        } finally {
            connection.rawExecSql("DETACH DATABASE $COPY")
        }
    }
    // Nothing of the unencrypted database may be left over to be applied to the encrypted one.
    deleteFiles("$path-wal", "$path-shm")
    check(rename(copyPath, path) == 0) { "Could not replace an unencrypted database with its encrypted copy" }
}

@OptIn(ExperimentalForeignApi::class)
private fun hasSqliteHeader(path: String): Boolean {
    val file = fopen(path, "rb") ?: return false
    val header = ByteArray(SQLITE_HEADER.size)
    val read = try {
        header.usePinned { fread(it.addressOf(0), 1.convert(), header.size.convert(), file) }
    } finally {
        fclose(file)
    }
    return read.toInt() == header.size && header.contentEquals(SQLITE_HEADER)
}

private fun deleteFiles(vararg paths: String) {
    paths.forEach { NSFileManager.defaultManager.removeItemAtPath(it, null) }
}

private fun String.toSqlLiteral(): String = "'${replace("'", "''")}'"

private const val COPY = "encrypted_copy"
private val SQLITE_HEADER = "SQLite format 3".encodeToByteArray() + byteArrayOf(0)
