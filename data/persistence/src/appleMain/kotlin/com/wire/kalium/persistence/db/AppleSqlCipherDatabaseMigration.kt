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
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.longForQuery
import co.touchlab.sqliter.withConnection
import platform.Foundation.NSFileManager
import platform.posix.remove
import platform.posix.rename

internal fun prepareEncryptedDatabaseIfNeeded(
    basePath: String?,
    dbName: String,
    passphrase: ByteArray?,
    useGradleSafeSqliterLogging: Boolean
) {
    val sqlCipherKey = passphrase?.toSqlCipherKey() ?: return
    val databasePath = DatabaseFileContext.databasePath(dbName, basePath)
    if (!databaseFileExists(databasePath) || canOpenDatabase(basePath, dbName, sqlCipherKey, useGradleSafeSqliterLogging)) {
        return
    }

    encryptPlaintextDatabase(
        basePath = basePath,
        dbName = dbName,
        databasePath = databasePath,
        sqlCipherKey = sqlCipherKey,
        useGradleSafeSqliterLogging = useGradleSafeSqliterLogging
    )
}

private fun canOpenDatabase(
    basePath: String?,
    dbName: String,
    sqlCipherKey: String?,
    useGradleSafeSqliterLogging: Boolean
): Boolean = runCatching {
    createDatabaseManager(
        noVersionCheckConfiguration(
            basePath = basePath,
            dbName = dbName,
            sqlCipherKey = sqlCipherKey,
            useGradleSafeSqliterLogging = useGradleSafeSqliterLogging
        )
    ).withConnection { connection ->
        connection.longForQuery(SQLITE_MASTER_COUNT_QUERY)
    }
}.isSuccess

private fun encryptPlaintextDatabase(
    basePath: String?,
    dbName: String,
    databasePath: String,
    sqlCipherKey: String,
    useGradleSafeSqliterLogging: Boolean
) {
    val temporaryEncryptedPath = "$databasePath$TEMPORARY_ENCRYPTED_SUFFIX"
    val plaintextBackupPath = "$databasePath$PLAINTEXT_BACKUP_SUFFIX"
    removeDatabaseFiles(temporaryEncryptedPath)
    removeDatabaseFiles(plaintextBackupPath)

    createDatabaseManager(
        noVersionCheckConfiguration(
            basePath = basePath,
            dbName = dbName,
            sqlCipherKey = null,
            useGradleSafeSqliterLogging = useGradleSafeSqliterLogging
        )
    ).withConnection { connection ->
        connection.rawExecSql("PRAGMA wal_checkpoint(FULL);")
        val userVersion = connection.longForQuery("PRAGMA user_version;")
        connection.rawExecSql(
            "ATTACH DATABASE '${temporaryEncryptedPath.escapeSql()}' AS encrypted KEY '${sqlCipherKey.escapeSql()}';"
        )
        connection.rawExecSql("SELECT sqlcipher_export('encrypted');")
        connection.rawExecSql("PRAGMA encrypted.user_version = $userVersion;")
        connection.rawExecSql("DETACH DATABASE encrypted;")
    }

    replacePlaintextDatabaseWithEncryptedCopy(
        databasePath = databasePath,
        temporaryEncryptedPath = temporaryEncryptedPath,
        plaintextBackupPath = plaintextBackupPath
    )
}

private fun noVersionCheckConfiguration(
    basePath: String?,
    dbName: String,
    sqlCipherKey: String?,
    useGradleSafeSqliterLogging: Boolean
) = DatabaseConfiguration(
    name = dbName,
    version = NO_VERSION_CHECK,
    create = { _ -> },
    loggingConfig = if (useGradleSafeSqliterLogging) {
        DatabaseConfiguration.Logging(logger = GradleSafeSqliterLogger)
    } else {
        DatabaseConfiguration.Logging()
    },
    encryptionConfig = DatabaseConfiguration.Encryption(sqlCipherKey)
).copy(
    extendedConfig = DatabaseConfiguration.Extended(basePath = basePath)
)

private fun replacePlaintextDatabaseWithEncryptedCopy(
    databasePath: String,
    temporaryEncryptedPath: String,
    plaintextBackupPath: String
) {
    check(rename(databasePath, plaintextBackupPath) == POSIX_SUCCESS) {
        "Unable to move plaintext database before SQLCipher migration: $databasePath"
    }
    removeDatabaseSidecars(databasePath)

    if (rename(temporaryEncryptedPath, databasePath) != POSIX_SUCCESS) {
        rename(plaintextBackupPath, databasePath)
        throw IllegalStateException("Unable to install encrypted database after SQLCipher migration: $databasePath")
    }

    removeDatabaseFiles(plaintextBackupPath)
    removeDatabaseSidecars(temporaryEncryptedPath)
}

internal fun ByteArray.toSqlCipherKey(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(radix = HEX_RADIX).padStart(HEX_BYTE_LENGTH, '0')
}

private fun databaseFileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

private fun removeDatabaseFiles(path: String) {
    remove(path)
    removeDatabaseSidecars(path)
}

private fun removeDatabaseSidecars(path: String) {
    remove("$path-journal")
    remove("$path-shm")
    remove("$path-wal")
}

private fun String.escapeSql() = replace(oldValue = "'", newValue = "''")

private const val HEX_RADIX = 16
private const val HEX_BYTE_LENGTH = 2
private const val SQLITE_MASTER_COUNT_QUERY = "SELECT count(*) FROM sqlite_master;"
private const val TEMPORARY_ENCRYPTED_SUFFIX = ".sqlcipher.tmp"
private const val PLAINTEXT_BACKUP_SUFFIX = ".plaintext.bak"
private const val POSIX_SUCCESS = 0
