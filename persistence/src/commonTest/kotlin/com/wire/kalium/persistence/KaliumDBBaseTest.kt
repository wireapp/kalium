package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.KaliumDatabaseProvider

expect abstract class KaliumDBBaseTest() {
        fun deleteDatabase()
        fun createDatabase(): KaliumDatabaseProvider
}
