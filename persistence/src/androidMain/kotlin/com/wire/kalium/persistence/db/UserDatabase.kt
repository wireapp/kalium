@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import net.sqlcipher.database.SupportFactory

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

fun UserDatabaseProvider(
    context: Context,
    userId: UserIDEntity,
    passphrase: UserDBSecret,
    encrypt: Boolean = true,
    dispatcher: CoroutineDispatcher
): UserDatabaseProvider {
    val dbName = FileNameUtil.userDBName(userId)

    val driver: AndroidSqliteDriver = if (encrypt) {
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = dbName,
            factory = SupportFactory(passphrase.value)
        )
    } else {
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = dbName
        )
    }
    val credentials = if (encrypt) {
        DatabaseCredentials.Passphrase(passphrase)
    } else {
        DatabaseCredentials.NotSet
    }
    return UserDatabaseProvider(userId, driver, dispatcher, PlatformDatabaseData(context, credentials))
}

fun inMemoryDatabase(
    context: Context,
    userId: UserIDEntity,
    dispatcher: CoroutineDispatcher
): UserDatabaseProvider {
    val passphrase = "testPass".toByteArray()
    val driver = AndroidSqliteDriver(
        schema = UserDatabase.Schema,
        context = context,
        name = null,
        factory = SupportFactory(passphrase)
    )
    return UserDatabaseProvider(
        userId, driver, dispatcher, PlatformDatabaseData(
            context, DatabaseCredentials.Passphrase(
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
