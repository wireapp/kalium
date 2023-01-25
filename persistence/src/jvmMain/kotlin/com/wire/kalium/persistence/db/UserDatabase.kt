/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import java.util.Properties

private const val DATABASE_NAME = "main.db"

internal actual class PlatformDatabaseData(
    val storePath: File?
)

fun userDatabaseBuilder(
    userId: UserIDEntity,
    storePath: File,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val databasePath = storePath.resolve(DATABASE_NAME)
    val databaseExists = databasePath.exists()

    // Make sure all intermediate directories exist
    storePath.mkdirs()

    val driver: SqlDriver = sqlDriver("jdbc:sqlite:${databasePath.absolutePath}")

    if (!databaseExists) {
        UserDatabase.Schema.create(driver)
    }
    return UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(storePath))
}

private fun sqlDriver(driverUri: String): SqlDriver = JdbcSqliteDriver(
    driverUri,
    Properties(1).apply { put("foreign_keys", "true") }
)

fun inMemoryDatabase(userId: UserIDEntity, dispatcher: CoroutineDispatcher): UserDatabaseBuilder {
    val driver = sqlDriver(JdbcSqliteDriver.IN_MEMORY)
    UserDatabase.Schema.create(driver)
    return UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(File("inMemory")))
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean = platformDatabaseData.storePath?.resolve(DATABASE_NAME)?.delete() ?: false
