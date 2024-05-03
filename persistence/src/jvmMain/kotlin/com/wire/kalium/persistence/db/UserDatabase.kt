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

@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import java.util.Properties
import javax.sql.DataSource

private const val DATABASE_NAME = "main.db"

actual data class PlatformDatabaseData(
    val storageData: StorageData
)

sealed interface StorageData {
    data class FileBacked(val file: File) : StorageData
    data object InMemory : StorageData
}

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    val storageData = platformDatabaseData.storageData
    if (storageData is StorageData.InMemory) {
        return inMemoryDatabase(userId, dispatcher)
    }
    if (storageData !is StorageData.FileBacked) {
        throw IllegalStateException("Unsupported storage data type: $storageData")
    }
    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on JVM")
    }

    val databasePath = storageData.file.resolve(DATABASE_NAME)
    val databaseExists = databasePath.exists()

    // Make sure all intermediate directories exist
    storageData.file.mkdirs()

    val driver: SqlDriver = sqlDriver(userId.toString(), false)

//     if (!databaseExists) {
        UserDatabase.Schema.create(driver)
//     }
    return UserDatabaseBuilder(userId, driver, dispatcher, platformDatabaseData, !passphrase.isNullOrBlank())
}

actual fun userDatabaseDriverByPath(
    platformDatabaseData: PlatformDatabaseData,
    path: String,
    passphrase: UserDBSecret?,
    enableWAL: Boolean
): SqlDriver = sqlDriver(path, false)

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? {
    val storageData = platformDatabaseData.storageData
    if (storageData !is StorageData.FileBacked) {
        return null
    }
    val dbFile = storageData.file.resolve(DATABASE_NAME)
    return if (dbFile.exists()) dbFile.absolutePath else null
}

private fun sqlDriver(userId: String, enableWAL: Boolean): SqlDriver = createDataSourceForUserDB(userId).asJdbcDriver()

private fun createDataSourceForUserDB(userId: String): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver" //todo. parameterize
        jdbcUrl = "jdbc:postgresql://localhost:5432/$userId"
        username = "postgres"
        password = ""
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    return HikariDataSource(dataSourceConfig)
}

/**
 * Creates an in-memory user database,
 * or returns an existing one if it already exists.
 *
 * @param userId The ID of the user for whom the database is created.
 * @param dispatcher The coroutine dispatcher to be used for executing database operations.
 * @return The user database builder.
 */
fun inMemoryDatabase(
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder = InMemoryDatabaseCache.getOrCreate(userId) {
    val driver = sqlDriver(JdbcSqliteDriver.IN_MEMORY, false)
    UserDatabase.Schema.create(driver)
    val storageData = StorageData.FileBacked(File("inMemory"))
    UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(storageData), false)
}

fun clearInMemoryDatabase(userId: UserIDEntity): Boolean {
    return InMemoryDatabaseCache.clearEntry(userId)
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean = when (val storageData = platformDatabaseData.storageData) {
    StorageData.InMemory -> clearInMemoryDatabase(userId)
    is StorageData.FileBacked -> storageData.file.resolve(DATABASE_NAME).delete()
}
