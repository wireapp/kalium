@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import platform.Foundation.NSFileManager

private const val DATABASE_NAME = "main.db"

sealed interface DatabaseCredentials {
    data class Passphrase(val value: String) : DatabaseCredentials
    object NotSet : DatabaseCredentials
}

// TODO encrypt database using sqlcipher
internal actual class PlatformDatabaseData(val storePath: String)

fun userDatabaseBuilder(
    userId: UserIDEntity,
    storePath: String,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    NSFileManager.defaultManager.createDirectoryAtPath(storePath, true, null, null)

    val schema = UserDatabase.Schema
    val driver = NativeSqliteDriver(
        DatabaseConfiguration(
            name = DATABASE_NAME,
            version = schema.version,
            create = { connection ->
                wrapConnection(connection) { schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                basePath = storePath,
                foreignKeyConstraints = true
            )
        )
    )

    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(storePath)
    )
}

fun inMemoryDatabase(
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val schema = UserDatabase.Schema
    val driver = NativeSqliteDriver(
        DatabaseConfiguration(
            name = FileNameUtil.userDBName(userId),
            version = schema.version,
            inMemory = true,
            create = { connection ->
                wrapConnection(connection) { schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                foreignKeyConstraints = true
            )
        )
    )
    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData("inMemory")
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return NSFileManager.defaultManager.removeItemAtPath(platformDatabaseData.storePath, null)
}
