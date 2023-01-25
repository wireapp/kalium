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

 actual class PlatformDatabaseData(
    val storePath: File
)

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on JVM")
    }

    if (enableWAL) {
        throw NotImplementedError("WAL is not supported on JVM")
    }

    val databasePath = platformDatabaseData.storePath.resolve(DATABASE_NAME)
    val databaseExists = databasePath.exists()

    // Make sure all intermediate directories exist
    platformDatabaseData.storePath.mkdirs()

    val driver: SqlDriver = sqlDriver("jdbc:sqlite:${databasePath.absolutePath}")

    if (!databaseExists) {
        UserDatabase.Schema.create(driver)
    }
    return UserDatabaseBuilder(userId, driver, dispatcher, platformDatabaseData)
}

private fun sqlDriver(driverUri: String): SqlDriver = JdbcSqliteDriver(
    driverUri,
    Properties(1).apply {
        put("foreign_keys", "true")
        put("journal_mode", "wal")
    }
)

fun inMemoryDatabase(userId: UserIDEntity, dispatcher: CoroutineDispatcher): UserDatabaseBuilder {
    val driver = sqlDriver(JdbcSqliteDriver.IN_MEMORY)
    UserDatabase.Schema.create(driver)
    return UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(File("inMemory")))
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean = platformDatabaseData.storePath.resolve(DATABASE_NAME).delete() ?: false


internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? {
    val dbFile = platformDatabaseData.storePath.resolve(DATABASE_NAME)
    return if (dbFile.exists()) dbFile.absolutePath else null
}
