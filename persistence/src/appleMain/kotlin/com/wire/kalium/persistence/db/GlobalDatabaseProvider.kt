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

import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSFileManager

actual fun globalDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher,
    enableWAL: Boolean
): GlobalDatabaseProvider {
    val driver = when (val data = platformDatabaseData.storageData) {
        is StorageData.FileBacked -> {
            NSFileManager.defaultManager.createDirectoryAtPath(data.storePath, true, null, null)
            val schema = GlobalDatabase.Schema
            DriverBuilder().withWALEnabled(false)
                .build(driverUri = data.storePath, dbName = FileNameUtil.globalDBName(), schema = schema)
        }

        StorageData.InMemory -> DriverBuilder()
            .withWALEnabled(false)
            .build(
                driverUri = null,
                dbName = FileNameUtil.globalDBName(),
                schema = UserDatabase.Schema
            )
    }

    return GlobalDatabaseProvider(driver, queriesContext, platformDatabaseData, false)
}

actual fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean {
    return (platformDatabaseData.storageData as? StorageData.FileBacked)?.storePath?.let {
        NSFileManager.defaultManager.removeItemAtPath(it, null)
    } ?: false
}
