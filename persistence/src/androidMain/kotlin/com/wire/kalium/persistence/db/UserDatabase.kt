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

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.support.SqliteCallback
import com.wire.kalium.persistence.db.support.SupportOpenHelperFactory
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher

sealed interface DatabaseCredentials {
    data class Passphrase(val value: UserDBSecret) : DatabaseCredentials
    object NotSet : DatabaseCredentials
}

/**
 * Platform-specific data used to create the database
 * that might be necessary for future operations
 * in the future like [nuke]
 */
internal actual class PlatformDatabaseData(
    val context: Context,
    val databaseCredentials: DatabaseCredentials
)

fun userDatabaseBuilder(
    context: Context,
    userId: UserIDEntity,
    passphrase: UserDBSecret,
    encrypt: Boolean = true,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val dbName = FileNameUtil.userDBName(userId)

    val driver: AndroidSqliteDriver = if (encrypt) {
        System.loadLibrary("sqlcipher")
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = dbName,
            factory = SupportOpenHelperFactory(passphrase.value, true)
        )
    } else {
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = dbName,
            callback = SqliteCallback(UserDatabase.Schema)
        )
    }
    val credentials = if (encrypt) {
        DatabaseCredentials.Passphrase(passphrase)
    } else {
        DatabaseCredentials.NotSet
    }
    return UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(context, credentials))
}

fun inMemoryDatabase(
    context: Context,
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseBuilder {
    val passphrase = "testPass".toByteArray()
    System.loadLibrary("sqlcipher")
    val driver = AndroidSqliteDriver(
        schema = UserDatabase.Schema,
        context = context,
        name = null,
        factory = SupportOpenHelperFactory(passphrase)
    )
    return UserDatabaseBuilder(
        userId = userId,
        sqlDriver = driver,
        dispatcher = dispatcher,
        platformDatabaseData = PlatformDatabaseData(
            context = context,
            databaseCredentials = DatabaseCredentials.Passphrase(
                UserDBSecret(passphrase)
            )
        )
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return platformDatabaseData.context.deleteDatabase(FileNameUtil.userDBName(userId))
}
