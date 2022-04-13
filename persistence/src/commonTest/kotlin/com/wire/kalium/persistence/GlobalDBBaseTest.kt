package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.GlobalDatabaseProvider

expect abstract class GlobalDBBaseTest() {
        fun deleteDatabase()
        fun createDatabase(): GlobalDatabaseProvider
}
