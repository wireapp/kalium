package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider

actual open class BaseDatabaseTest actual constructor() {
    actual fun deleteDatabase() {
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider()
    }

}
