package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.TestDispatcher

expect open class BaseDatabaseTest() {

    protected val dispatcher: TestDispatcher
    fun deleteDatabase()
    fun createDatabase(): UserDatabaseProvider

}
