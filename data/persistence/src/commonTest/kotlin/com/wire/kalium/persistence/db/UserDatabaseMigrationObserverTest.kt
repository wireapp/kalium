/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class UserDatabaseMigrationObserverTest {
    @Test
    fun givenObservedSchema_whenCreatingDatabase_thenMigrationIsNotReported() {
        var migrationCount = 0
        val schema = TestSchema().withMigrationObserver { migrationCount += 1 }

        schema.create(mock<SqlDriver>())

        assertEquals(0, migrationCount)
    }

    @Test
    fun givenObservedSchema_whenMigratingDatabase_thenMigrationIsReportedOnce() {
        var migrationCount = 0
        val schema = TestSchema().withMigrationObserver { migrationCount += 1 }

        schema.migrate(mock<SqlDriver>(), oldVersion = 1, newVersion = 2)

        assertEquals(1, migrationCount)
    }

    private class TestSchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = 2

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion
        ): QueryResult.Value<Unit> = QueryResult.Unit
    }
}
