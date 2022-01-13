package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    suspend fun createDriver(): SqlDriver
}
