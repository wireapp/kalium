package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.db.KaliumDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil

actual abstract class KaliumDBBaseTest {
    actual fun deleteDatabase() {
        deleteDatabase(FileNameUtil.appDBName())
    }

    actual fun createDatabase(): KaliumDatabaseProvider {
        return KaliumDatabaseProvider("123456789")
    }
}
