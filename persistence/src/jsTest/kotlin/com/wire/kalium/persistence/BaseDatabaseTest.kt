package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    actual fun deleteDatabase() {
        // TODO delete test database
    }

    actual fun createDatabase(): UserDatabaseProvider {
        TODO("Not yet implemented")
    }

}
