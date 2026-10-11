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

@file:Suppress("MatchingDeclarationName", "TooGenericExceptionCaught")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.sqlite3.SQLITE_OK
import co.touchlab.sqliter.sqlite3.SQLITE_OPEN_READONLY
import co.touchlab.sqliter.sqlite3.SQLITE_ROW
import co.touchlab.sqliter.sqlite3.sqlite3_close
import co.touchlab.sqliter.sqlite3.sqlite3_column_text
import co.touchlab.sqliter.sqlite3.sqlite3_finalize
import co.touchlab.sqliter.sqlite3.sqlite3_open_v2
import co.touchlab.sqliter.sqlite3.sqlite3_prepare_v2
import co.touchlab.sqliter.sqlite3.sqlite3_step
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSURL

/**
 * An existing-store failure. If [cleanupUncertain] is true, the account owner must retain its lease
 * until process exit: the native driver can lose a connection during partial opening or failed close.
 */
class ExistingDatabaseException(
    val reason: Reason,
    val cleanupUncertain: Boolean = false,
    cause: Throwable? = null,
) : IllegalStateException("Existing database is unavailable ($reason)", cause) {
    enum class Reason { INCOMPATIBLE, UNAVAILABLE }
}

/** Caller holds the account lease. Never creates the main database, migrates, or inserts SelfUser. */
fun existingUserDatabaseBuilder(
    storagePath: String,
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher,
): UserDatabaseBuilder {
    val driver = existingDatabaseDriver(storagePath, FileNameUtil.userDBName(userId), UserDatabase.Schema.version)
    return try {
        UserDatabaseBuilder(
            userId = userId,
            sqlDriver = driver,
            dispatcher = dispatcher,
            platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(storagePath)),
            isEncrypted = false,
            dbInvalidationController = DbInvalidationController(enabled = false, notifyKey = driver::notifyListeners),
            initializeSelfUser = false,
        )
    } catch (failure: Throwable) {
        throw uncertainOpenFailure(driver, failure)
    }
}

internal fun existingDatabaseDriver(storagePath: String, name: String, schemaVersion: Long): SqlDriver {
    require(storagePath.startsWith('/')) { "The prepared database path must be absolute" }
    val journalMode = readExistingSchema("${storagePath.trimEnd('/')}/$name", schemaVersion)
    return openExistingNativeDriver(storagePath, name, schemaVersion, journalMode)
}

internal fun openExistingNativeDriver(storagePath: String, name: String, schemaVersion: Long, journalMode: JournalMode): SqlDriver {
    // SQLiter strips file://. A file:/ URI preserves SQLite's URI mode and escaped path characters.
    val directoryUri = "file:" + NSURL.fileURLWithPath(storagePath).absoluteString!!.removePrefix("file://")
    val driver = nativeDatabaseDriver(
        DatabaseConfiguration(
            name = "$name?mode=rw",
            version = NO_VERSION_CHECK,
            journalMode = journalMode,
            create = { error("Existing database creation is forbidden") },
            upgrade = { _, _, _ -> error("Existing database migration is forbidden") },
            extendedConfig = DatabaseConfiguration.Extended(basePath = directoryUri, foreignKeyConstraints = true),
        ),
        maxReaderConnections = 1,
    )
    return try {
        // Initialize both one-connection pools here. Otherwise the first caller write could fail
        // before its connection is registered, outside the conservative partial-open boundary.
        driver.execute(null, "PRAGMA foreign_keys = 1", 0).value
        val actualVersion = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            check(cursor.next().value)
            QueryResult.Value(cursor.getLong(0))
        }, 0).value
        if (actualVersion != schemaVersion) throw ExistingDatabaseException(ExistingDatabaseException.Reason.INCOMPATIBLE)
        driver
    } catch (failure: Throwable) {
        throw uncertainOpenFailure(driver, failure)
    }
}

private fun uncertainOpenFailure(driver: SqlDriver, failure: Throwable): ExistingDatabaseException {
    // Close known handles, but a failed lazy connection may never have entered the driver's pool.
    try {
        driver.close()
    } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
    }
    return ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE, cleanupUncertain = true, cause = failure)
}

private fun readExistingSchema(path: String, schemaVersion: Long): JournalMode = withReadOnlyDatabase(path) { database ->
    val actualVersion = readTextColumn(database, "PRAGMA user_version").toLongOrNull()
    if (actualVersion != schemaVersion) throw ExistingDatabaseException(ExistingDatabaseException.Reason.INCOMPATIBLE)
    when (readTextColumn(database, "PRAGMA journal_mode")) {
        "wal" -> JournalMode.WAL
        "delete" -> JournalMode.DELETE
        else -> throw ExistingDatabaseException(ExistingDatabaseException.Reason.INCOMPATIBLE)
    }
}

private fun <T> withReadOnlyDatabase(path: String, block: (CPointer<sqlite3>) -> T): T = memScoped {
    val pointer = alloc<CPointerVar<sqlite3>>()
    pointer.value = null
    val result = sqlite3_open_v2(path, pointer.ptr, SQLITE_OPEN_READONLY, null)
    val database = pointer.value
    try {
        if (result != SQLITE_OK || database == null) throw ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE)
        block(database)
    } finally {
        // sqlite3_close, unlike close_v2, proves no statement still keeps this connection alive.
        if (database != null && sqlite3_close(database) != SQLITE_OK) {
            throw ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE, cleanupUncertain = true)
        }
    }
}

private fun readTextColumn(database: CPointer<sqlite3>, sql: String): String = memScoped {
    val statement = alloc<CPointerVar<sqlite3_stmt>>()
    statement.value = null
    try {
        if (sqlite3_prepare_v2(database, sql.cstr.ptr, -1, statement.ptr, null) != SQLITE_OK ||
            sqlite3_step(statement.value) != SQLITE_ROW
        ) {
            throw ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE)
        }
        sqlite3_column_text(statement.value, 0)?.reinterpret<ByteVar>()?.toKString()
            ?: throw ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE)
    } finally {
        // Finalize always destroys the statement, even when it returns its last execution error.
        statement.value?.let {
            if (sqlite3_finalize(it) != SQLITE_OK) throw ExistingDatabaseException(ExistingDatabaseException.Reason.UNAVAILABLE)
        }
    }
}
