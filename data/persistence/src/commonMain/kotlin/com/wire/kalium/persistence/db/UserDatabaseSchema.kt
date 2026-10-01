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

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.wire.kalium.persistence.UserDatabase

/** Selects the migration history for upstream and older Super Kalium databases. */
internal object UserDatabaseSchema : SqlSchema<QueryResult.AsyncValue<Unit>> by UserDatabase.Schema {
    @Suppress("MagicNumber") // These versions name the unchanged numbered SQLDelight migrations.
    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue {
        var migrationVersion = oldVersion
        if (oldVersion in 144L..149L && oldVersion < newVersion && driver.hasLegacyMeetingSchema()) {
            // The old fork applied local migrations 143..148 without upstream's TZID conversion.
            // Run the unchanged upstream conversion, then skip the already completed local operations.
            UserDatabase.Schema.migrate(driver, 143, 144).await()
            migrationVersion++
        }
        UserDatabase.Schema.migrate(driver, migrationVersion, newVersion, *callbacks).await()
    }

    private suspend fun SqlDriver.hasLegacyMeetingSchema(): Boolean = executeQuery(null, "PRAGMA table_info(Meeting)", { cursor ->
        val columns = mutableSetOf<String>()
        while (cursor.next().value) cursor.getString(1)?.let(columns::add)
        QueryResult.Value("trial" in columns && "tzid" !in columns)
    }, 0).await()
}
