package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.Database

actual open class BaseDatabaseTest actual constructor() {
    actual fun deleteDatabase() {
    }

    actual fun createDatabase(): Database {
        return Database()
    }

}
