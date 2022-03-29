package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.util.FileNameUtil

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("user_12_id", "user_12_domain")

    actual fun deleteDatabase() {
        deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(): Database {
        return Database(userId, "123456789")
    }

}
