package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.GlobalDatabaseProvider

actual abstract class GlobalDBBaseTest {
    actual fun deleteDatabase() {
        TODO("Not yet implemented")
    }

    actual fun createDatabase(): GlobalDatabaseProvider {
        TODO("Not yet implemented")
    }
}
