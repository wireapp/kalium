@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

actual class PlatformDatabaseData()

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    if (passphrase != null) {
        throw NotImplementedError("Encrypted DB is not supported on iOS")
    }
    val driver = NativeSqliteDriver(UserDatabase.Schema, FileNameUtil.userDBName(userId))

    return UserDatabaseBuilder(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData()
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
        PlatformDatabaseData()
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean = TODO()

// TODO: implement this in Darwin main when merging with develop
internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? = TODO()
