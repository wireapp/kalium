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

import app.cash.sqldelight.db.SqlDriver
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
    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on JVM")
    }

    val driver = when (val storageData = platformDatabaseData.storageData) {
        is StorageData.InMemory -> return createGlobalInMemoryDatabase(queriesContext)
        is StorageData.FileBacked -> {
            val databasePath = storageData.file.resolve(FileNameUtil.globalDBName())
            val databaseExists = databasePath.exists()
            storageData.file.mkdirs()
            val driver = databaseDriver("jdbc:sqlite:${databasePath.absolutePath}") {
                isWALEnabled = enableWAL
                areForeignKeyConstraintsEnforced = true
            }
            if (!databaseExists) {
                GlobalDatabase.Schema.create(driver)
            }
            driver
        }

        is StorageData.RDBMS -> {
            val driver = createDataSource(storageData).asJdbcDriver()
            val databaseExists = driver.databaseExists(FileNameUtil.globalDBName())
            if (!databaseExists) {
                GlobalDatabase.Schema.create(driver)
            }
            driver
        }
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

private fun createDataSource(storageData: StorageData.RDBMS): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = storageData.uri
        username = storageData.username
        password = storageData.password
        maximumPoolSize = 3
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    return HikariDataSource(dataSourceConfig)
}

fun SqlDriver.databaseExists(dbName: String): Boolean {
    val result = executeQuery(
        Int.MIN_VALUE, """SELECT table_name FROM information_schema.tables where table_name = 'accounts'""",
        {
            if (it.next().value) {
                val result = it.getString(0)
                println("THE RESULT: ${result}")
                app.cash.sqldelight.db.QueryResult.Value(result?.isNotEmpty())
            } else {
                app.cash.sqldelight.db.QueryResult.Value(false)
            }

        }, 0
    ) { }
    println("Exists: ${result.value}")
    return result.value ?: false
}
