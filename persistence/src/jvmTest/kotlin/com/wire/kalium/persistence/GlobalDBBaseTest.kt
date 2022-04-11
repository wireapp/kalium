package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import java.nio.file.Files

actual abstract class GlobalDBBaseTest {
    private val databaseFile  = Files.createTempDirectory("test-storage").toFile().resolve("test-kalium.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): GlobalDatabaseProvider {
        return GlobalDatabaseProvider(databaseFile)
    }
}
