package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.DatabaseDriverFactory

expect open class BaseDatabaseTest() {

    fun deleteDatabase()
    fun createDatabaseDriverFactory(): DatabaseDriverFactory

}
