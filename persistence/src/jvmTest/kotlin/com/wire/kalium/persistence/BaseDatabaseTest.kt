package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    private val databaseFile = Files.createTempDirectory("test-storage").toFile().resolve("test.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(userId, databaseFile, dispatcher = dispatcher)
    }

}
