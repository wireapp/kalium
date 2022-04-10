package com.wire.kalium.persistence

import java.nio.file.Files
import com.wire.kalium.persistence.db.UserDatabaseProvider

actual open class BaseDatabaseTest actual constructor() {

    private val databaseFile  = Files.createTempDirectory("test-storage").toFile().resolve("test.db")

    actual fun deleteDatabase() {
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(databaseFile)
    }

}
