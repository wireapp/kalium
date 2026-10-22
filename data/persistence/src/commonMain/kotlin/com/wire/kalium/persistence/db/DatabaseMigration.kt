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

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooGenericExceptionCaught")
internal suspend fun SqlDriver.migrateSchema(sqlSchema: SqlSchema<QueryResult.AsyncValue<Unit>>): Boolean = try {
    // Pin the writer connection for temporary tables and atomically commit the schema version.
    object : SuspendingTransacterImpl(this) {}.transaction {
        val oldVersion = executeQuery(null, "PRAGMA user_version", { cursor ->
            check(cursor.next().value)
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        }, 0).await()
        require(oldVersion <= sqlSchema.version) { "Database schema is newer than supported" }
        if (oldVersion != sqlSchema.version) {
            sqlSchema.awaitMigrate(this@migrateSchema, oldVersion, sqlSchema.version)
            execute(null, "PRAGMA user_version = ${sqlSchema.version}", 0).await()
        }
    }
    true
} catch (exception: CancellationException) {
    throw exception
} catch (_: Exception) {
    false
}
