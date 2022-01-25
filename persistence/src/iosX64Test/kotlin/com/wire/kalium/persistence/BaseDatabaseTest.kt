package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.db.Database

actual open class BaseDatabaseTest actual constructor() {
    private val name: String = "test.db"

    actual fun deleteDatabase() {
        deleteDatabase(name)
    }

    actual fun createDatabase(): Database {
        return Database(name, "123456789")
    }

}
