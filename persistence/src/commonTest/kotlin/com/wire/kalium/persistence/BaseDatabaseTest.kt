package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider

expect open class BaseDatabaseTest() {

    fun deleteDatabase()
    fun createDatabase(): UserDatabaseProvider

}
