package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.KaliumDatabaseProvider

actual abstract class KaliumDBBaseTest {
    actual fun deleteDatabase() {
        TODO("Not yet implemented")
    }

    actual fun createDatabase(): KaliumDatabaseProvider {
        TODO("Not yet implemented")
    }
}
