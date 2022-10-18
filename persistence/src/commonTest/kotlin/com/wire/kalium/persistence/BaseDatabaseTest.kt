package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.TestDispatcher

expect open class BaseDatabaseTest() {

    protected val dispatcher: TestDispatcher
    fun deleteDatabase(
        userId: UserIDEntity = DefaultDatabaseTestValues.userId
    )

    fun createDatabase(
        userId: UserIDEntity = DefaultDatabaseTestValues.userId
    ): UserDatabaseBuilder

}

object DefaultDatabaseTestValues {
    val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")
}
