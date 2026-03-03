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

actual fun globalDatabaseProvider(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher,
    passphrase: GlobalDatabaseSecret?,
    enableWAL: Boolean,
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

    val schema = GlobalDatabase.Schema
    val databasePath = storageData.file.resolve(FileNameUtil.globalDBName())

    // Make sure all intermediate directories exist
    storageData.file.mkdirs()
    val url = "jdbc:sqlite:${databasePath.absolutePath}"
    val driver = databaseDriver(uri = url, schema = schema) {
        isWALEnabled = enableWAL
        areForeignKeyConstraintsEnforced = true
    }

    return GlobalDatabaseBuilder(driver, platformDatabaseData, queriesContext)
}

actual fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean {
    return (platformDatabaseData.storageData as? StorageData.FileBacked)?.file?.resolve(FileNameUtil.globalDBName())?.delete() ?: false
}

fun createGlobalInMemoryDatabase(dispatcher: CoroutineDispatcher): GlobalDatabaseBuilder {
    val driver = databaseDriver(uri = JdbcSqliteDriver.IN_MEMORY, schema = GlobalDatabase.Schema) {
        isWALEnabled = false
        areForeignKeyConstraintsEnforced = true
    }
    return GlobalDatabaseBuilder(driver, PlatformDatabaseData(StorageData.InMemory), dispatcher)
}
