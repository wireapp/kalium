package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    val UserIDEntity.databaseFile
        get() = Files.createTempDirectory("test-storage").toFile().resolve("test-$domain-$value.db")

    actual fun databasePath(
        userId: UserIDEntity
    ): String {
        return userId.databaseFile.path
    }

    actual fun deleteDatabase(userId: UserIDEntity) {
        userId.databaseFile.delete()
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseBuilder {
        return userDatabaseBuilder(userId, userId.databaseFile, dispatcher = dispatcher)
    }

}
