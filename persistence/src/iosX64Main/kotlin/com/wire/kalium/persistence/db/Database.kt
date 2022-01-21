package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl

actual class Database(name: String, passphrase: String) {

    val database: AppDatabase

    init {
        val driver = NativeSqliteDriver(AppDatabase.Schema, name)
        database = AppDatabase(driver, User.Adapter(qualified_idAdapter = QualifiedIDAdapter()))
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries)
}
