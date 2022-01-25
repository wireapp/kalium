package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.Database

actual open class BaseDatabaseTest actual constructor() {

    actual fun deleteDatabase() {
        // TODO delete test database
    }

    actual fun createDatabase(): Database {
        TODO("Not yet implemented")
    }

}
