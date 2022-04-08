package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider

actual open class BaseDatabaseTest actual constructor() {

    actual fun deleteDatabase() {
        // TODO delete test database
    }

    actual fun createDatabase(): UserDatabaseProvider {
        TODO("Not yet implemented")
    }

}
