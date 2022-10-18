package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.TestDispatcher

internal actual fun createTestDatabase(
    userId: UserIDEntity,
    dispatcher: TestDispatcher
): UserDatabaseBuilder = TODO("JavaScript Database not yet supported")

internal actual fun deleteTestDatabase(userId: UserIDEntity) { TODO("JavaScript Database not yet supported") }

internal actual fun createTestGlobalDatabase(): GlobalDatabaseProvider = TODO("JavaScript Database not yet supported")

internal actual fun deleteTestGlobalDatabase() { TODO("JavaScript Database not yet supported") }
