package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")

    actual fun deleteDatabase() {
        deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(userId, "123456789")
    }

}
