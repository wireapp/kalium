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
import co.touchlab.sqliter.sqlite3.SQLITE_OK
import co.touchlab.sqliter.sqlite3.SQLITE_OPEN_READWRITE
import co.touchlab.sqliter.sqlite3.sqlite3_close_v2
import co.touchlab.sqliter.sqlite3.sqlite3_exec
import co.touchlab.sqliter.sqlite3.sqlite3_open_v2
import co.touchlab.sqliter.withConnection
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.posix.EINTR
import platform.posix.LOCK_EX
import platform.posix.LOCK_UN
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.close
import platform.posix.errno
import platform.posix.fclose
import platform.posix.flock
import platform.posix.fopen
import platform.posix.fread
import platform.posix.mode_t
import platform.posix.open
import platform.posix.rename

/** The encrypted copy is written next to the database under this suffix, then renamed over it. */
internal const val ENCRYPTED_COPY_SUFFIX = ".encrypting"

/** The lock that serializes the encryption of one database is taken on a file next to it with this suffix. */
internal const val ENCRYPTION_LOCK_SUFFIX = ".encryption-lock"

/**
 * Encrypts an Apple database that an earlier version of Kalium stored unencrypted, before a driver opens it with [key].
 *
 * Only a file that starts with the SQLite header can be unencrypted: an encrypted database starts with SQLCipher's
 * random salt. An encrypted database with a plaintext header starts with the SQLite header too, so whether such a file
 * is unencrypted is decided by reading it without a key.
 *
 * That check and the encryption run under an exclusive lock on a file next to the database. Of several processes or
 * SDK instances that open the same database, e.g. an app and its extension, only one encrypts it; the others find it
 * encrypted once they get the lock. The system releases the lock when the process holding it ends.
 *
 * The unencrypted file is exported with `sqlcipher_export` into an encrypted copy, which is renamed over it. The export
 * runs with a rollback journal, which checkpoints a WAL database first. Until the rename, the unencrypted file stays
 * intact, and a copy left behind by an interrupted run is deleted before the next one.
 *
 * A connection may open the database only after this function has returned.
 */
internal fun encryptPlaintextDatabase(directory: String, name: String, key: String) {
    val path = "$directory/$name"
    if (!hasSqliteHeader(path)) return
    withFileLock("$path$ENCRYPTION_LOCK_SUFFIX") {
        if (isReadableWithoutKey(path)) encrypt(directory, name, key)
    }
}

private fun encrypt(directory: String, name: String, key: String) {
    kaliumLogger.i("Encrypting a database that an earlier version stored unencrypted")
    val path = "$directory/$name"
    val copyPath = "$path$ENCRYPTED_COPY_SUFFIX"
    deleteFiles(copyPath)
    createDatabaseManager(configurationWithoutKey(directory, name)).withConnection { connection ->
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

/**
 * Whether the database can be read without a key. Only then is it unencrypted. An encrypted one can't be read: behind
 * its plaintext header, if it has one, SQLite finds encrypted bytes and reports an error, which one depends on those
 * bytes. The check writes nothing. It calls the same SQLite that SQLiter uses.
 */
@OptIn(ExperimentalForeignApi::class)
private fun isReadableWithoutKey(path: String): Boolean = memScoped {
    val database = allocPointerTo<cnames.structs.sqlite3>()
    try {
        val opened = sqlite3_open_v2(path, database.ptr, SQLITE_OPEN_READWRITE, null)
        check(opened == SQLITE_OK) { "Could not open a database to check whether it is encrypted, SQLite error $opened" }
        sqlite3_exec(database.value, "SELECT count(*) FROM sqlite_schema", null, null, null) == SQLITE_OK
    } finally {
        sqlite3_close_v2(database.value)
    }
}

private fun configurationWithoutKey(directory: String, name: String) = DatabaseConfiguration(
    name = name,
    version = NO_VERSION_CHECK,
    create = {},
    journalMode = JournalMode.DELETE,
    extendedConfig = DatabaseConfiguration.Extended(basePath = directory)
)

/** Runs [block] under an exclusive `flock` on [lockPath], waiting for other holders. The lock file is kept. */
@OptIn(ExperimentalForeignApi::class)
private fun withFileLock(lockPath: String, block: () -> Unit) {
    val file = open(lockPath, O_RDWR or O_CREAT or O_CLOEXEC, (S_IRUSR or S_IWUSR).convert<mode_t>())
    check(file >= 0) { "Could not open the lock file for encrypting a database, errno $errno" }
    try {
        while (flock(file, LOCK_EX) != 0) {
            check(errno == EINTR) { "Could not lock the database for encrypting it, errno $errno" }
        }
        try {
            block()
        } finally {
            flock(file, LOCK_UN)
        }
    } finally {
        close(file)
    }
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
