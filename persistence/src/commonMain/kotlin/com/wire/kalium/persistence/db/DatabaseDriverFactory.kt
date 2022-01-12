package com.wire.kalium.persistence.db

import com.squareup.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    suspend fun createDriver(): SqlDriver
}
