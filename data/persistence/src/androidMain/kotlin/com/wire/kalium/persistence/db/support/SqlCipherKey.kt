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

private const val SQLCIPHER_RAW_KEY_BYTES = 32
private const val SQLCIPHER_RAW_KEY_PAYLOAD_BYTES = 67
private const val RAW_KEY_PREFIX_LENGTH = 2
private const val HEX_CHARS_PER_BYTE = 2
private const val NIBBLE_BITS = 4
private const val LOW_NIBBLE_MASK = 0x0F
private const val UNSIGNED_BYTE_MASK = 0xFF
private val hexDigits = "0123456789abcdef".encodeToByteArray()

/**
 * Converts 32 bytes of random key material to SQLCipher's raw-key BLOB representation.
 *
 * The returned payload is `x'<64 hex characters>'`. SQLCipher uses those 32 bytes directly
 * instead of applying its password KDF.
 */
internal fun ByteArray.toSqlCipherRawKey(): ByteArray {
    require(size == SQLCIPHER_RAW_KEY_BYTES) {
        "SQLCipher raw keys must contain exactly $SQLCIPHER_RAW_KEY_BYTES bytes"
    }

    return ByteArray(SQLCIPHER_RAW_KEY_PAYLOAD_BYTES).also { result ->
        result[0] = 'x'.code.toByte()
        result[1] = '\''.code.toByte()
        forEachIndexed { index, byte ->
        val unsignedByte = byte.toInt() and UNSIGNED_BYTE_MASK
        result[RAW_KEY_PREFIX_LENGTH + index * HEX_CHARS_PER_BYTE] = hexDigits[unsignedByte ushr NIBBLE_BITS]
        result[RAW_KEY_PREFIX_LENGTH + index * HEX_CHARS_PER_BYTE + 1] = hexDigits[unsignedByte and LOW_NIBBLE_MASK]
        }
        result[result.lastIndex] = '\''.code.toByte()
    }
}

/**
 * Selects the key representation for the global database and eagerly rekeys a legacy database.
 *
 * If migration fails but the legacy database is still readable, availability wins and the legacy
 * representation is returned. A later process start will retry the migration.
 */
@Suppress("TooGenericExceptionCaught")
internal fun globalDatabaseKey(
    databaseFile: File,
    secret: ByteArray,
    migrationRawKey: ByteArray?,
    onMigrationComplete: () -> Unit
): ByteArray {
    if (migrationRawKey == null || !databaseFile.exists()) return secret

    val selectedKey = try {
        rekeyDatabase(databaseFile, secret, migrationRawKey)
        check(canOpenDatabase(databaseFile, migrationRawKey, verifyIntegrity = true)) {
            "Global database could not be validated after raw-key migration"
        }
        onMigrationComplete()
        migrationRawKey
    } catch (migrationFailure: RuntimeException) {
        when {
            canOpenDatabase(databaseFile, migrationRawKey, verifyIntegrity = true) -> {
                onMigrationComplete()
                migrationRawKey
            }

            canOpenDatabase(databaseFile, secret) -> {
                val message = "Failed to migrate the global database to a SQLCipher raw key; continuing with its legacy key"
                kaliumLogger.w(message, migrationFailure)
                secret
            }

            else -> throw migrationFailure
        }
    }
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

private fun hasActiveCipher(database: SQLiteDatabase): Boolean =
    database.rawQuery("PRAGMA cipher_status", emptyArray()).use { cursor ->
        cursor.moveToFirst() && cursor.getInt(0) == 1
    }

private fun hasValidCipherIntegrity(database: SQLiteDatabase): Boolean =
    database.rawQuery("PRAGMA cipher_integrity_check", emptyArray()).use { cursor ->
        !cursor.moveToFirst()
    }
