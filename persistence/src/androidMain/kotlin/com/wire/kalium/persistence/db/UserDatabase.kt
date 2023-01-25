@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

sealed interface DatabaseCredentials {
    data class Passphrase(val value: UserDBSecret) : DatabaseCredentials
    object NotSet : DatabaseCredentials
}

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
            factory = SupportOpenHelperFactory(passphrase.value, null, enableWAL)
        )
    } else {
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = platformDatabaseData.context,
            name = dbName,
            callback = SqliteCallback(UserDatabase.Schema, enableWAL)
        )
    }
    return UserDatabaseBuilder(userId, driver, dispatcher, platformDatabaseData)
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
        platformDatabaseData = PlatformDatabaseData(context = context)
    )
}

internal actual fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return platformDatabaseData.context.deleteDatabase(FileNameUtil.userDBName(userId))
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
