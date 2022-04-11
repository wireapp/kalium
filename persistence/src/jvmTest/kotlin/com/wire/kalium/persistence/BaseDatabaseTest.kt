package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.UserDatabaseProvider
import java.nio.file.Files

actual open class BaseDatabaseTest actual constructor() {

    private val databaseFile  = Files.createTempDirectory("test-storage").toFile().resolve("test.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(databaseFile)
    }

}
