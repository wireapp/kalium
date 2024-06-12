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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

actual fun pgGlobalDatabaseProvider(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher,
    passphrase: GlobalDatabaseSecret?,
): GlobalDatabaseBuilder {
    val storageData = platformDatabaseData.storageData
    if (storageData is StorageData.InMemory) {
        return createGlobalInMemoryDatabase(queriesContext)
    }

    if (storageData !is StorageData.FileBacked) {
        throw IllegalStateException("Unsupported storage data type: $storageData")
    }

    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on JVM")
    }

    val databasePath = storageData.file.resolve(FileNameUtil.globalDBName())
    val databaseExists = databasePath.exists()

    // Make sure all intermediate directories exist
    storageData.file.mkdirs()
    val driver = databaseDriver("jdbc:sqlite:${databasePath.absolutePath}") {
        isWALEnabled = enableWAL
        areForeignKeyConstraintsEnforced = true
    }

    if (!databaseExists) {
        GlobalDatabase.Schema.create(driver)
    }

    return GlobalDatabaseBuilder(driver, platformDatabaseData, queriesContext)
}

actual fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean {
    return (platformDatabaseData.storageData as? StorageData.FileBacked)?.file?.resolve(FileNameUtil.globalDBName())?.delete() ?: false
}

fun createGlobalInMemoryDatabase(dispatcher: CoroutineDispatcher): GlobalDatabaseBuilder {
    val driver = databaseDriver(JdbcSqliteDriver.IN_MEMORY) {
        isWALEnabled = false
        areForeignKeyConstraintsEnforced = true
    }
    GlobalDatabase.Schema.create(driver)
    return GlobalDatabaseBuilder(driver, PlatformDatabaseData(StorageData.InMemory), dispatcher)
}
