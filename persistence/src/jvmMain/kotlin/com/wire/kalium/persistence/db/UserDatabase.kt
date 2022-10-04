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
    val storePath: File
)

fun UserDatabaseProvider(
    userId: UserIDEntity,
    storePath: File,
    dispatcher: CoroutineDispatcher
): UserDatabaseProvider {
    val databasePath = storePath.resolve(DATABASE_NAME)
    val databaseExists = databasePath.exists()

    // Make sure all intermediate directories exist
    storePath.mkdirs()

    val driver: SqlDriver = JdbcSqliteDriver(
        "jdbc:sqlite:${databasePath.absolutePath}",
        Properties(1).apply { put("foreign_keys", "true") }
    )

    if (!databaseExists) {
        UserDatabase.Schema.create(driver)
    }
    return UserDatabaseProvider(userId, driver, dispatcher, PlatformDatabaseData(storePath))
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean = platformDatabaseData.storePath.resolve(DATABASE_NAME).delete()
