package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.DatabaseDriverFactory

actual open class BaseDatabaseTest actual constructor() {

    actual fun deleteDatabase() {
        // TODO delete test database
    }

    actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
        return DatabaseDriverFactory()
    }

}
