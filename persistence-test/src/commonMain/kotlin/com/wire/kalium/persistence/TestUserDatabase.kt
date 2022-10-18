package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

class TestUserDatabase(
    val userId: UserIDEntity,
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) {

    val builder: UserDatabaseBuilder

    init {
        deleteTestDatabase(userId)
        builder = createTestDatabase(userId, dispatcher)
    }

    fun delete() {
        deleteTestDatabase(userId)
    }
}

internal fun getTempDatabaseFileNameForUser(userId: UserIDEntity) = "TEMP-TEST-DB-${userId.value}.${userId.domain}.db"

internal expect fun deleteTestDatabase(userId: UserIDEntity)

internal expect fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseBuilder
