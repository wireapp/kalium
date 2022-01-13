package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.DatabaseDriverFactory

actual open class BaseDatabaseTest actual constructor() {

    actual fun deleteDatabase() { }

    actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
        return DatabaseDriverFactory()
    }

}
