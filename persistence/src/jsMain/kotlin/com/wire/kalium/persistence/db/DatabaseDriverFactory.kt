package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.sqljs.initSqlDriver
import kotlinx.coroutines.await

actual class DatabaseDriverFactory {

    actual suspend fun createDriver(): SqlDriver {
        return initSqlDriver(AppDatabase.Schema).await()
    }

}
