package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import net.sqlcipher.database.SupportFactory

actual class Database(context: Context, name: String, passphrase: String) {

    val database: AppDatabase

    init {
        val supportFactory = SupportFactory(passphrase.toByteArray())
        val driver =  AndroidSqliteDriver(AppDatabase.Schema, context, name, factory = supportFactory)
        database = AppDatabase(driver, User.Adapter(qualified_idAdapter = QualifiedIDAdapter()))
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(queries = database.usersQueries )
}
