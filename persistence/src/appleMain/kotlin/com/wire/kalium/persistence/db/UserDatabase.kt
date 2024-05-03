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
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
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
actual class PlatformDatabaseData(val storePath: String)

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    NSFileManager.defaultManager.createDirectoryAtPath(platformDatabaseData.storePath, true, null, null)
    val schema = UserDatabase.Schema

    val driver = NativeSqliteDriver(
        DatabaseConfiguration(
            name = FileNameUtil.userDBName(userId),
            version = schema.version.toInt(),
            journalMode = if (enableWAL) JournalMode.WAL else JournalMode.DELETE,
            create = { connection ->
                wrapConnection(connection) { schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                basePath = platformDatabaseData.storePath
            )
        )
    )

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
    val driver = NativeSqliteDriver(
        DatabaseConfiguration(
            name = FileNameUtil.userDBName(userId),
            version = schema.version.toInt(),
            inMemory = true,
            create = { connection ->
                wrapConnection(connection) { schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
            }
        )
    )
    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(""),
        false
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return NSFileManager.defaultManager.removeItemAtPath(platformDatabaseData.storePath, null)
}

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? = if (NSURL.fileURLWithPath(platformDatabaseData.storePath).checkResourceIsReachableAndReturnError(null) ?: false)
    platformDatabaseData.storePath else null
