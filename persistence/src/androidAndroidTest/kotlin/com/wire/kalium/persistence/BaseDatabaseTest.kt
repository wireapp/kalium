package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()
    actual val encryptedDBSecret: UserDBSecret = UserDBSecret(ByteArray(0))

    actual fun deleteDatabase(userId: UserIDEntity) {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseBuilder {
        return userDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            userId = userId,
            encrypt = false,
            passphrase = encryptedDBSecret,
            dispatcher = dispatcher
        )
    }

    actual fun databasePath(userId: UserIDEntity): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        return context.getDatabasePath(FileNameUtil.userDBName(userId)).absolutePath
    }

}
