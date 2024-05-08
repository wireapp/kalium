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

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.db.support.SqliteCallback
import com.wire.kalium.persistence.db.support.SupportOpenHelperFactory
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

actual fun globalDatabaseProvider(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher,
    passphrase: GlobalDatabaseSecret?,
    enableWAL: Boolean,
    encryptionEnabled: Boolean
): GlobalDatabaseBuilder {
    val schema = GlobalDatabase.Schema
    val dbName = FileNameUtil.globalDBName()
    val driver = if (encryptionEnabled) {
        System.loadLibrary("sqlcipher")
        AndroidSqliteDriver(
            schema = schema,
            context = platformDatabaseData.context,
            name = dbName,
            factory = SupportOpenHelperFactory(passphrase!!.value, enableWAL)
        )
    } else {
        AndroidSqliteDriver(
            schema = schema,
            context = platformDatabaseData.context,
            name = dbName,
            callback = SqliteCallback(schema, enableWAL)
        )
    }
    return GlobalDatabaseBuilder(driver, platformDatabaseData, queriesContext)
}

actual fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean {
    // driver.close()
    return platformDatabaseData.context.deleteDatabase(FileNameUtil.globalDBName())
}
