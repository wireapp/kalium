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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig

/**
 * Wrapper that allows to build a JdbcSqliteDriver with custom configuration.
 * This is useful to enable WAL mode, foreign keys, etc. and other SQLite specific settings for fine-tuning.
 */
actual class DriverBuilder {
    private val sqLiteConfig = SQLiteConfig()

    init {
        withForeignKeys(true)
    }

    actual fun withWALEnabled(enableWAL: Boolean): DriverBuilder {
        sqLiteConfig.setJournalMode(if (enableWAL) SQLiteConfig.JournalMode.WAL else SQLiteConfig.JournalMode.DELETE)
        return this
    }

    actual fun withForeignKeys(enforceForeignKeys: Boolean): DriverBuilder {
        sqLiteConfig.enforceForeignKeys(enforceForeignKeys)
        return this
    }

    actual fun build(driverUri: String): SqlDriver {
        return JdbcSqliteDriver(driverUri, sqLiteConfig.toProperties())
    }

    actual fun build(
        driverUri: String?,
        dbName: String,
        schema: SqlSchema<QueryResult.Value<Unit>>
    ): SqlDriver {
        throw IllegalStateException("Builder not supported for this platform.")
    }
}
