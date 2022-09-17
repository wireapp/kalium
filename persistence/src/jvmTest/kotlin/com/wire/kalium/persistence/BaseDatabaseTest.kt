package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    private val databaseFile  = Files.createTempDirectory("test-storage").toFile().resolve("test.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(databaseFile, dispatcher = dispatcher)
    }

}
