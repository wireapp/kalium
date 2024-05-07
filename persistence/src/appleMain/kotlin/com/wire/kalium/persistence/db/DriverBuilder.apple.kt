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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode

actual class DriverBuilder {
    private var enableWAL: Boolean = false
    private var enforceForeignKeys: Boolean = false

    actual fun withWALEnabled(enableWAL: Boolean): DriverBuilder {
        this.enableWAL = enableWAL
        return this
    }

    actual fun withForeignKeys(enforceForeignKeys: Boolean): DriverBuilder {
        this.enforceForeignKeys = enforceForeignKeys
        return this
    }

    actual fun build(driverUri: String): SqlDriver {
        throw IllegalStateException("Builder not supported for this platform.")
    }

    actual fun build(
        driverUri: String?,
        dbName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>
    ): SqlDriver {
        val inMemory = driverUri == null
        return NativeSqliteDriver(
            DatabaseConfiguration(
                name = dbName,
                version = schema.version.toInt(),
                journalMode = if (enableWAL) JournalMode.WAL else JournalMode.DELETE,
                inMemory = inMemory,
                create = { connection ->
                    wrapConnection(connection) { schema.create(it) }
                },
                upgrade = { connection, oldVersion, newVersion ->
                    wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
                }
            ).copy(
                extendedConfig = if (inMemory) DatabaseConfiguration.Extended(
                    foreignKeyConstraints = enforceForeignKeys
                ) else DatabaseConfiguration.Extended(
                    basePath = driverUri,
                    foreignKeyConstraints = enforceForeignKeys
                )
            )
        )
    }
}
