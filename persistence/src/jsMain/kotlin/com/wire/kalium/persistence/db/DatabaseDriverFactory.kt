package com.wire.kalium.persistence.db

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.sqljs.initSqlDriver
import kotlinx.coroutines.await

actual class DatabaseDriverFactory {

    actual suspend fun createDriver(): SqlDriver {
        return initSqlDriver(AppDatabase.Schema).await()
    }

}
