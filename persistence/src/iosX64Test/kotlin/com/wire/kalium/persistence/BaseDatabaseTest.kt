package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    actual fun deleteDatabase(userId: UserIDEntity) {
        deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseProvider {
        return UserDatabaseProvider(userId, "123456789", dispatcher)
    }
}
