package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()
    actual val encryptedDBSecret: UserDBSecret = UserDBSecret(ByteArray(0))

    actual fun deleteDatabase(userId: UserIDEntity) {
        // TODO delete test database
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseBuilder {
        TODO("Not yet implemented")
    }

    actual fun databasePath(
        userId: UserIDEntity
    ): String {
        TODO("Not yet implemented")
    }

}
