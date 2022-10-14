package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.TestDispatcher

internal actual fun createTestDatabase(
    userId: UserIDEntity,
    dispatcher: TestDispatcher
): UserDatabaseProvider = TODO("JavaScript Database not yet supported")

internal actual fun deleteTestDatabase(userId: UserIDEntity) { TODO("JavaScript Database not yet supported") }

internal actual fun createTestGlobalDatabase(): GlobalDatabaseProvider = TODO("JavaScript Database not yet supported")

internal actual fun deleteTestGlobalDatabase() { TODO("JavaScript Database not yet supported") }


