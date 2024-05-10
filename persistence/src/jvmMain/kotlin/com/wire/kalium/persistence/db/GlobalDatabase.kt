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

import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.util.FileNameUtil
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineDispatcher
import javax.sql.DataSource

actual fun globalDatabaseProvider(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher,
    passphrase: GlobalDatabaseSecret?,
    enableWAL: Boolean,
): GlobalDatabaseBuilder {
    val storageData = platformDatabaseData.storageData
    if (storageData is StorageData.InMemory) {
        return createGlobalInMemoryDatabase(queriesContext)
    }

    if (storageData !is StorageData.FileBacked) {
        throw IllegalStateException("Unsupported storage data type: $storageData")
    }

    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on JVM")
    }

    // Make sure all intermediate directories exist
    // storageData.file.mkdirs()
    val driver = createDataSource("jdbc:postgresql://localhost:5432/${FileNameUtil.globalDBName()}").asJdbcDriver()
    val databaseExists = driver.databaseExists(FileNameUtil.globalDBName())
    if (!databaseExists) {
        GlobalDatabase.Schema.create(driver)
    }

    return GlobalDatabaseBuilder(driver, platformDatabaseData, queriesContext)
}

actual fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean {
    return (platformDatabaseData.storageData as? StorageData.FileBacked)?.file?.resolve(FileNameUtil.globalDBName())?.delete() ?: false
}

fun createGlobalInMemoryDatabase(dispatcher: CoroutineDispatcher): GlobalDatabaseBuilder {
    val driver = databaseDriver(JdbcSqliteDriver.IN_MEMORY) {
        isWALEnabled = false
        areForeignKeyConstraintsEnforced = true
    }
    GlobalDatabase.Schema.create(driver)
    return GlobalDatabaseBuilder(driver, PlatformDatabaseData(StorageData.InMemory), dispatcher)
}

private fun createDataSource(driverUri: String): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = driverUri
        username = "global"
        password = "global"
        maximumPoolSize = 3
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    return HikariDataSource(dataSourceConfig)
}

fun JdbcDriver.databaseExists(dbName: String): Boolean {
    val result = executeQuery(
        Int.MIN_VALUE, """SELECT datname FROM pg_catalog.pg_database WHERE datname = ?""",
        {
            if (it.next().value) {
                val result = it.getString(0)
                println("THE RESULT: ${result}")
                app.cash.sqldelight.db.QueryResult.Value(result?.isNotEmpty())
            } else {
                app.cash.sqldelight.db.QueryResult.Value(false)
            }

        }, 0
    ) { bindString(0, dbName) }
    println("Exists: ${result.value}")
    return result.value ?: false
}
