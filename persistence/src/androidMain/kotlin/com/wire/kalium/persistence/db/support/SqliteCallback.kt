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

package com.wire.kalium.persistence.db.support

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import com.wire.kalium.persistence.db.AndroidSqliteDriver

internal class SqliteCallback(
    schema: SqlSchema<QueryResult.Value<Unit>>,
    private val enableWAL: Boolean
) : SupportSQLiteOpenHelper.Callback(schema.version.toInt()) {
    private val baseCallback = AndroidSqliteDriver.Callback(schema)
    override fun onCreate(db: SupportSQLiteDatabase) = baseCallback.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        baseCallback.onUpgrade(
            db,
            oldVersion,
            newVersion
        )

    override fun onConfigure(db: SupportSQLiteDatabase) {
        super.onConfigure(db)
        if (enableWAL) {
            db.enableWriteAheadLogging()
        }
    }
}
