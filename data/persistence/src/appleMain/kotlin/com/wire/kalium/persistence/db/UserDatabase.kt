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

@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@Suppress("LongParameterList")
actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean,
    dbInvalidationControlEnabled: Boolean
): UserDatabaseBuilder {
    val rawDriver = when (platformDatabaseData.storageData) {
        is StorageData.FileBacked -> {
            NSFileManager.defaultManager.createDirectoryAtPath(
                platformDatabaseData.storageData.storePath,
                true,
                null,
                null
            )
            databaseDriver(platformDatabaseData.storageData.storePath, FileNameUtil.userDBName(userId), UserDatabase.Schema) {
                isWALEnabled = enableWAL
            }
        }

        StorageData.InMemory ->
            databaseDriver(null, FileNameUtil.userDBName(userId), UserDatabase.Schema) {
                isWALEnabled = false
            }
    }

    val invalidationController = DbInvalidationController(
        enabled = dbInvalidationControlEnabled,
        notifyKey = { key -> rawDriver.notifyListeners(key) }
    )

    val driver: SqlDriver = MutedSqlDriver(
        delegate = rawDriver,
        invalidationController = invalidationController
    )

    return UserDatabaseBuilder(
        userId = userId,
        sqlDriver = driver,
        dispatcher = dispatcher,
        platformDatabaseData = platformDatabaseData,
        isEncrypted = passphrase != null,
        dbInvalidationController = invalidationController
    )
}

actual fun userDatabaseDriverByPath(
    platformDatabaseData: PlatformDatabaseData,
    path: String,
    passphrase: UserDBSecret?,
    enableWAL: Boolean
): SqlDriver {
    return NativeSqliteDriver(
        UserDatabase.Schema,
        path
    )
}

/**
 * Creates an in-memory user database,
 * or returns an existing one if it already exists.
 *
 * @param userId The ID of the user for whom the database is created.
 * @param dispatcher The coroutine dispatcher to be used for executing database operations.
 * @return The user database builder.
 */
fun inMemoryDatabase(
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder = InMemoryDatabaseCache.getOrCreate(userId) {
    val rawDriver = databaseDriver(null, FileNameUtil.userDBName(userId), UserDatabase.Schema) {
        isWALEnabled = false
    }

    val invalidationController = DbInvalidationController(
        enabled = false,
        notifyKey = { key -> rawDriver.notifyListeners(key) }
    )

    val driver: SqlDriver = MutedSqlDriver(
        delegate = rawDriver,
        invalidationController = invalidationController
    )

    UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(StorageData.InMemory),
        false,
        invalidationController
    )
}

/**
 * Clears the in-memory database for the given user.
 * This closes the database connection and removes it from the cache,
 * causing SQLite to delete the shared in-memory database.
 *
 * @param userId The ID of the user whose database should be cleared.
 * @return `true` if the database was cleared, `false` if it didn't exist.
 */
fun clearInMemoryDatabase(userId: UserIDEntity): Boolean {
    return InMemoryDatabaseCache.clearEntry(userId)
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return when (platformDatabaseData.storageData) {
        is StorageData.FileBacked -> NSFileManager.defaultManager.removeItemAtPath(platformDatabaseData.storageData.storePath, null)
        is StorageData.InMemory -> clearInMemoryDatabase(userId)
    }
}

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? {
    return if (
        platformDatabaseData.storageData is StorageData.FileBacked && NSURL.fileURLWithPath(platformDatabaseData.storageData.storePath)
            .checkResourceIsReachableAndReturnError(null)
    ) {
        platformDatabaseData.storageData.storePath
    } else {
        null
    }
}

internal actual fun createEmptyDatabaseFile(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
): String? = TODO()
