package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.KaliumDatabaseProvider
import java.nio.file.Files

actual abstract class KaliumDBBaseTest {
    private val databaseFile  = Files.createTempDirectory("test-storage").toFile().resolve("test-kalium.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): KaliumDatabaseProvider {
        return KaliumDatabaseProvider(databaseFile)
    }
}
