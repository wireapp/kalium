package com.wire.kalium.persistence

import co.touchlab.sqliter.DatabaseFileContext
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.TestDispatcher

internal actual fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseBuilder {
    return userDatabaseBuilder(userId, "123456789", dispatcher)
}

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    DatabaseFileContext.deleteDatabase(FileNameUtil.userDBName(userId))
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseProvider {
    return GlobalDatabaseProvider("123456789")
}

internal actual fun deleteTestGlobalDatabase() {
    DatabaseFileContext.deleteDatabase(FileNameUtil.globalDBName())
}
