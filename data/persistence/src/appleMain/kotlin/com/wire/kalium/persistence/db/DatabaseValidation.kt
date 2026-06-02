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

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooGenericExceptionCaught")
actual suspend fun SqlDriver.migrate(sqlSchema: SqlSchema<QueryResult.AsyncValue<Unit>>): Boolean {
    val oldVersion = executeQuery(null, "PRAGMA user_version;", {
        it.next().value
        QueryResult.Value(it.getLong(0))
    }, 0).value ?: return false

    val newVersion = sqlSchema.version
    return try {
        if (oldVersion != newVersion) {
            sqlSchema.synchronous().migrate(this, oldVersion, newVersion).value
        }
        true
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        false
    }
}

actual suspend fun SqlDriver.checkFKViolations(): Boolean =
    executeQuery(null, "PRAGMA foreign_key_check;", {
        QueryResult.Value(it.next().value)
    }, 0).value
