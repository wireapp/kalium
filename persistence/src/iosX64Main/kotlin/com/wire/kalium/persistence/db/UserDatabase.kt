@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

internal actual class PlatformDatabaseData(val passphrase: String)

fun UserDatabaseProvider(
    userId: UserIDEntity,
    passphrase: String,
    dispatcher: CoroutineDispatcher
): UserDatabaseProvider {
    val driver = NativeSqliteDriver(UserDatabase.Schema, FileNameUtil.userDBName(userId))
    return UserDatabaseProvider(
        userId,
        driver,
        dispatcher,
        PlatformDatabaseData(passphrase)
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean = TODO()
