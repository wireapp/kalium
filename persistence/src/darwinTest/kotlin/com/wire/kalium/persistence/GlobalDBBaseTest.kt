package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil

actual abstract class GlobalDBBaseTest {
    actual fun deleteDatabase() {
        deleteDatabase(FileNameUtil.globalDBName())
    }

    actual fun createDatabase(): GlobalDatabaseProvider {
        return GlobalDatabaseProvider("123456789")
    }
}
