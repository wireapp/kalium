package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files

internal actual fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseProvider {
    val dbFile = getTempDatabaseFile(getTempDatabaseFileNameForUser(userId))
    return UserDatabaseProvider(userId, dbFile, dispatcher)
}

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    getTempDatabaseFile(getTempDatabaseFileNameForUser(userId)).delete()
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseProvider {
    return GlobalDatabaseProvider(getGlobalDatabaseFile())
}

internal actual fun deleteTestGlobalDatabase() {
    getGlobalDatabaseFile().delete()
}

private fun getGlobalDatabaseFile() = getTempDatabaseFile("TEST_GLOBAL_DATABASE.db")

private fun getTempDatabaseFile(fileName: String) = Files.createTempDirectory("test-storage").toFile().resolve(fileName)
