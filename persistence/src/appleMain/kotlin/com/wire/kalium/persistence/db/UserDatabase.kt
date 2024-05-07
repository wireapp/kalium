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

sealed interface DatabaseCredentials {
    data class Passphrase(val value: String) : DatabaseCredentials
    data object NotSet : DatabaseCredentials
}

// TODO encrypt database using sqlcipher
actual data class PlatformDatabaseData(
    val storageData: StorageData
)

sealed class StorageData {
    data class FileBacked(val storePath: String) : StorageData()
    data object InMemory : StorageData()
}

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    val driver = when (platformDatabaseData.storageData) {
        is StorageData.FileBacked -> {
            NSFileManager.defaultManager.createDirectoryAtPath(
                platformDatabaseData.storageData.storePath,
                true,
                null,
                null
            )
            DriverBuilder().withWALEnabled(enableWAL)
                .build(
                    driverUri = platformDatabaseData.storageData.storePath,
                    dbName = FileNameUtil.userDBName(userId),
                    schema = UserDatabase.Schema
                )
        }

        StorageData.InMemory -> DriverBuilder()
            .withWALEnabled(false)
            .build(
                driverUri = null,
                dbName = FileNameUtil.userDBName(userId),
                schema = UserDatabase.Schema
            )
    }

    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        platformDatabaseData,
        passphrase != null
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

fun inMemoryDatabase(
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val schema = UserDatabase.Schema

    val driver = DriverBuilder()
        .withWALEnabled(false)
        .build(
            driverUri = null,
            dbName = FileNameUtil.userDBName(userId),
            schema = UserDatabase.Schema
        )

    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(StorageData.InMemory),
        false
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return when (platformDatabaseData.storageData) {
        is StorageData.FileBacked -> NSFileManager.defaultManager.removeItemAtPath(platformDatabaseData.storageData.storePath, null)
        is StorageData.InMemory -> false
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
