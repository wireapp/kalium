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

package com.wire.kalium.persistence.db.support

import android.database.sqlite.SQLiteException
import com.wire.kalium.persistence.kaliumLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/** Rekeys [databaseFile] from [legacyKey] to [rawKey]. Injectable so callers can test the failure paths. */
internal typealias DatabaseRekey = (databaseFile: File, legacyKey: ByteArray, rawKey: ByteArray) -> Unit

/**
 * Returns the key the global database should be opened with, rekeying it to [rawKey] first if it is
 * still on [legacyKey].
 *
 * Migration state is derived from the database itself rather than tracked in a flag: [legacyKey] is
 * non-null only while the legacy alias is still stored, and a database that already opens with
 * [rawKey] needs no rekey. That makes "rekeyed but the process died before the legacy alias was
 * cleared" an ordinary path instead of a special case.
 *
 * If the rekey fails but the legacy key still opens the database, availability wins and the legacy
 * key is returned; a later process start retries.
 *
 * Callers must serialize invocations for a given database file — a concurrent caller rekeying the
 * same file would break an in-flight connection.
 */
internal fun globalDatabaseKey(
    databaseFile: File,
    rawKey: ByteArray,
    legacyKey: ByteArray?,
    rekey: DatabaseRekey = ::rekeyDatabase,
    onMigrated: () -> Unit
): ByteArray {
    if (legacyKey == null || !databaseFile.isNonEmpty()) return rawKey

    val onRawKey = canOpenDatabase(databaseFile, rawKey) || rekeyToRawKey(databaseFile, rawKey, legacyKey, rekey)
    return if (onRawKey) {
        // Safe to run after the fact: the database is already on the raw key and the raw key is
        // already stored, so a failure here only leaves the legacy alias behind for the next start.
        onMigrated()
        rawKey
    } else {
        legacyKey
    }
}

/**
 * Returns true once the database is on [rawKey], false if the rekey failed but [legacyKey] still
 * opens it — availability wins and a later process start retries.
 *
 * Throws when neither key works, since there is nothing left to fall back to.
 */
@Suppress("TooGenericExceptionCaught")
private fun rekeyToRawKey(
    databaseFile: File,
    rawKey: ByteArray,
    legacyKey: ByteArray,
    rekey: DatabaseRekey
): Boolean = try {
    rekey(databaseFile, legacyKey, rawKey)
    check(canOpenDatabase(databaseFile, rawKey, verifyIntegrity = true)) {
        "Global database could not be validated after raw-key migration"
    }
    true
} catch (migrationFailure: RuntimeException) {
    if (!canOpenDatabase(databaseFile, legacyKey)) throw migrationFailure

    val message = "Failed to migrate the global database to a SQLCipher raw key; continuing with its legacy key"
    kaliumLogger.w(message, migrationFailure)
    false
}

/** An interrupted first run can leave a zero-length file behind, which is not a database to migrate. */
private fun File.isNonEmpty(): Boolean = exists() && length() > 0

private fun rekeyDatabase(databaseFile: File, legacyKey: ByteArray, rawKey: ByteArray) {
    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        legacyKey,
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null
    )
    try {
        // Leaving WAL checkpoints as part of the mode switch and reports the mode it ended up in, so
        // this doubles as the checkpoint. A rollback journal also makes `changePassword` recoverable
        // if the process dies mid-rekey.
        database.rawQuery("PRAGMA journal_mode=DELETE", emptyArray()).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true)) {
                "Could not leave WAL mode before global database raw-key migration"
            }
        }
        database.changePassword(rawKey)
    } finally {
        database.close()
    }
}

internal fun canOpenDatabase(
    databaseFile: File,
    key: ByteArray,
    verifyIntegrity: Boolean = false
): Boolean {
    var database: SQLiteDatabase? = null
    return try {
        database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            key,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        hasReadableSchema(database) && (!verifyIntegrity || hasValidCipherIntegrity(database))
    } catch (_: SQLiteException) {
        false
    } finally {
        database?.close()
    }
}

/**
 * Reading the schema is what proves the key is correct: SQLCipher cannot return `sqlite_schema` rows
 * for a file it failed to decrypt, it throws instead.
 */
private fun hasReadableSchema(database: SQLiteDatabase): Boolean =
    database.rawQuery("SELECT COUNT(*) FROM sqlite_schema", emptyArray()).use { cursor ->
        cursor.moveToFirst()
    }

/** `PRAGMA cipher_integrity_check` returns a row per problem found, so no rows means consistent. */
private fun hasValidCipherIntegrity(database: SQLiteDatabase): Boolean =
    database.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use { cursor ->
        !cursor.moveToFirst()
    }
