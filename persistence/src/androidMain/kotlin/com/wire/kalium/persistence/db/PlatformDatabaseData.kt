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

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.db.support.SqliteCallback
import com.wire.kalium.persistence.db.support.SupportOpenHelperFactory

/**
 * Platform-specific data used to create the database
 * that might be necessary for future operations
 * in the future like [nuke]
 */
actual class PlatformDatabaseData(
    val context: Context
)

@Suppress("LongParameterList")
fun databaseDriver(
    context: Context,
    dbName: String,
    passphrase: ByteArray? = null,
    schema: SqlSchema<QueryResult.Value<Unit>>,
    config: DriverConfigurationBuilder.() -> Unit = {}
): SqlDriver {
    val driverConfiguration = DriverConfigurationBuilder().apply(config)
    val enableWAL = driverConfiguration.isWALEnabled
    return if (passphrase != null) {
        System.loadLibrary("sqlcipher")
        AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = dbName,
            factory = SupportOpenHelperFactory(passphrase, enableWAL)
        )
    } else {
        AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = dbName,
            callback = SqliteCallback(schema, enableWAL)
        )
    }
}
