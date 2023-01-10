@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

sealed interface DatabaseCredentials {
    data class Passphrase(val value: String) : DatabaseCredentials
    object NotSet : DatabaseCredentials
}

internal actual class PlatformDatabaseData(val credentials: DatabaseCredentials)

fun userDatabaseBuilder(
    userId: UserIDEntity,
    passphrase: String,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val driver = NativeSqliteDriver(UserDatabase.Schema, FileNameUtil.userDBName(userId))

    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(DatabaseCredentials.Passphrase(passphrase))
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
            }
        )
    )
    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(DatabaseCredentials.NotSet)
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean = TODO()
