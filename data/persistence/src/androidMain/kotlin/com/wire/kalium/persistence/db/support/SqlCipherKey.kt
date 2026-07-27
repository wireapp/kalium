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
 * Selects the key representation for the global database and eagerly rekeys a legacy database.
 *
 * If migration fails but the legacy database is still readable, availability wins and the legacy
 * representation is returned. A later process start will retry the migration.
 *
 * Callers must serialize invocations for a given database file: this opens, rekeys and promotes in
 * separate steps, and a concurrent caller rekeying the same file would break an in-flight connection.
 */
@Suppress("TooGenericExceptionCaught")
internal fun globalDatabaseKey(
    databaseFile: File,
    secret: ByteArray,
    migrationRawKey: ByteArray?,
    rekey: DatabaseRekey = ::rekeyDatabase,
    onMigrationComplete: () -> Unit
): ByteArray {
    if (migrationRawKey == null || !databaseFile.exists()) return secret

    // `onMigrationComplete` is deliberately invoked outside the try/catch: it persists the promoted
    // key alias and can throw, and re-entering it from the catch would escape unhandled.
    val selectedKey = try {
        rekey(databaseFile, secret, migrationRawKey)
        check(canOpenDatabase(databaseFile, migrationRawKey, verifyIntegrity = true)) {
            "Global database could not be validated after raw-key migration"
        }
        migrationRawKey
    } catch (migrationFailure: RuntimeException) {
        when {
            canOpenDatabase(databaseFile, migrationRawKey, verifyIntegrity = true) -> migrationRawKey

            canOpenDatabase(databaseFile, secret) -> {
                val message = "Failed to migrate the global database to a SQLCipher raw key; continuing with its legacy key"
                kaliumLogger.w(message, migrationFailure)
                secret
            }

            else -> throw migrationFailure
        }
    }
    if (selectedKey === migrationRawKey) onMigrationComplete()
    return selectedKey
}

private fun rekeyDatabase(databaseFile: File, legacyKey: ByteArray, rawKey: ByteArray) {
    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        legacyKey,
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null
    )
    try {
        database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                "Could not checkpoint the global database before raw-key migration"
            }
        }
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
        hasReadableSchema(database) &&
                hasActiveCipher(database) &&
                (!verifyIntegrity || hasValidCipherIntegrity(database))
    } catch (_: SQLiteException) {
        false
    } finally {
        database?.close()
    }
}

private fun hasReadableSchema(database: SQLiteDatabase): Boolean =
    database.rawQuery("SELECT COUNT(*) FROM sqlite_schema", emptyArray()).use { cursor ->
        cursor.moveToFirst()
    }

/**
 * REQUIRES SQLCipher >= 4.12.0, which is where `PRAGMA cipher_status` was introduced.
 *
 * On an older core SQLite silently ignores the unknown pragma and returns no rows, so this would
 * always report `false`. That makes [canOpenDatabase] fail for the raw key *and* for the legacy
 * fallback, turning every migration attempt into a hard failure and leaving the global database
 * unopenable. Do not downgrade `sqlcipher-android` below 4.12.0 without replacing this check.
 */
private fun hasActiveCipher(database: SQLiteDatabase): Boolean =
    database.rawQuery("PRAGMA cipher_status", emptyArray()).use { cursor ->
        cursor.moveToFirst() && cursor.getInt(0) == 1
    }

private fun hasValidCipherIntegrity(database: SQLiteDatabase): Boolean =
    database.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use { cursor ->
        !cursor.moveToFirst()
    }
