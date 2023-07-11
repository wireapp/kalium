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
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.support.SqliteCallback
import com.wire.kalium.persistence.db.support.SupportOpenHelperFactory
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

private const val DEFAULT_CACHE_SIZE = 20

/**
 * Platform-specific data used to create the database
 * that might be necessary for future operations
 * in the future like [nuke]
 */
actual class PlatformDatabaseData(
    val context: Context
)

actual fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean
): UserDatabaseBuilder {
    val dbName = FileNameUtil.userDBName(userId)

    val driver: AndroidSqliteDriver = if (passphrase != null) {
        System.loadLibrary("sqlcipher")
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = platformDatabaseData.context,
            name = dbName,
            factory = SupportOpenHelperFactory(
                passphrase.value,
                enableWriteAheadLogging = enableWAL
            )
        )
    } else {
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = platformDatabaseData.context,
            name = dbName,
            callback = SqliteCallback(UserDatabase.Schema, enableWAL = enableWAL)
        )
    }
    return UserDatabaseBuilder(userId, driver, dispatcher, platformDatabaseData, passphrase != null)
}

actual fun userDatabaseDriverByPath(
    platformDatabaseData: PlatformDatabaseData,
    path: String,
    passphrase: UserDBSecret?,
    enableWAL: Boolean
): SqlDriver {
    System.loadLibrary("sqlcipher")
    val db: SupportSQLiteDatabase = SQLiteDatabase.openDatabase(
        path,
        passphrase?.value,
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null
    )
    return AndroidSqliteDriver(db, DEFAULT_CACHE_SIZE)
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
        platformDatabaseData = PlatformDatabaseData(context = context),
        true
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    val context = platformDatabaseData.context
    return context.getDatabasePath(FileNameUtil.userDBName(userId))?.let {
        if (it.exists()) {
            context.deleteDatabase(FileNameUtil.userDBName(userId))
        } else {
            true
        }
    } ?: true
}

internal actual fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String? {
    val dbFile: File = platformDatabaseData.context.getDatabasePath(FileNameUtil.userDBName(userId))
    return if (dbFile.exists()) {
        dbFile.absolutePath
    } else {
        null
    }
}
