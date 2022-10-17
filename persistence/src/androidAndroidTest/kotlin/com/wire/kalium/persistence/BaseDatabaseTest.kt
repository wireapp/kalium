package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    actual fun deleteDatabase(userId: UserIDEntity) {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseBuilder {
        return userDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            userId,
            UserDBSecret("db_secret".toByteArray()),
            dispatcher = dispatcher
        )
    }
}
