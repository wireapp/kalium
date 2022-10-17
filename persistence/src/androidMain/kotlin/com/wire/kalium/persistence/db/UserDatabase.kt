@file:Suppress("MatchingDeclarationName")
package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import net.sqlcipher.database.SupportFactory

/**
 * Platform-specific data used to create the database
 * that might be necessary for future operations
 * in the future like [nuke]
 */
internal actual class PlatformDatabaseData(
    val context: Context,
    val passphrase: UserDBSecret,
    val isEncrypted: Boolean
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
    return UserDatabaseBuilder(userId, driver, dispatcher, PlatformDatabaseData(context, passphrase, encrypt))
}

internal actual fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean {
    return platformDatabaseData.context.deleteDatabase(FileNameUtil.userDBName(userId))
}
