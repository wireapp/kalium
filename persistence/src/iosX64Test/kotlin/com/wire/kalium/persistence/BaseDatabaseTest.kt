package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.DatabaseDriverFactory
import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase

actual open class BaseDatabaseTest actual constructor() {
    private val name: String = "main.db"

    actual fun deleteDatabase() {
        deleteDatabase(name)
    }

    actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
        return DatabaseDriverFactory(name)
    }

}
