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

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CoroutineDispatcher

actual class PlatformDatabaseData

@Suppress("LongParameterList")
actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean,
    dbInvalidationControlEnabled: Boolean
): UserDatabaseBuilder {
    val rawDriver = createKaliumWebWorkerDriver()
    val initializedDriver = SchemaInitializingSqlDriver(rawDriver) {
        UserDatabase.Schema.create(rawDriver).await()
        rawDriver.execute(
            identifier = null,
            sql = "INSERT OR IGNORE INTO SelfUser(id) VALUES(?);",
            parameters = 1
        ) {
            bindString(0, userId.toString())
        }.await()
        rawDriver.execute(
            identifier = null,
            sql = "PRAGMA foreign_keys = 1;",
            parameters = 0,
        ).await()
    }
    val invalidationController = DbInvalidationController(
        enabled = dbInvalidationControlEnabled,
        notifyKey = { key -> initializedDriver.notifyListeners(key) }
    )
    val driver: SqlDriver = MutedSqlDriver(
        delegate = initializedDriver,
        invalidationController = invalidationController
    )
    return UserDatabaseBuilder(
        userId = userId,
        sqlDriver = driver,
        dispatcher = dispatcher,
        platformDatabaseData = platformDatabaseData,
        isEncrypted = false,
        dbInvalidationController = invalidationController
    )
}

actual fun userDatabaseDriverByPath(
    platformDatabaseData: PlatformDatabaseData,
    path: String,
    passphrase: UserDBSecret?,
    enableWAL: Boolean
): SqlDriver {
    // TODO: Honor the requested JS database identity instead of ignoring path;
    //  the current worker driver setup always opens an anonymous DB.
    return createKaliumWebWorkerDriver()
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    // TODO: Implement real JS database deletion once the worker driver uses a stable persisted storage key.
    return true
}

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? {
    // TODO: Replace this file-path contract for JS with a storage-capability API; browser-backed DBs do not expose absolute paths.
    return null
}

internal actual fun createEmptyDatabaseFile(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
): String? {
    // TODO: Implement a JS-compatible export target flow instead of relying on native temporary database file creation.
    return null
}
