package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.Database

expect open class BaseDatabaseTest() {

    fun deleteDatabase()
    fun createDatabase(): Database

}
