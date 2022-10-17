package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.TestDispatcher

internal actual fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseProvider {
    return UserDatabaseProvider(
        ApplicationProvider.getApplicationContext(),
        userId,
        UserDBSecret("db_secret".toByteArray()),
        dispatcher = dispatcher
    )
}

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    val context: Context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(getTempDatabaseFileNameForUser(userId))
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseProvider {
    return GlobalDatabaseProvider(ApplicationProvider.getApplicationContext(), GlobalDatabaseSecret("test_db_secret".toByteArray()))
}

internal actual fun deleteTestGlobalDatabase() {
    val context: Context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(FileNameUtil.globalDBName())
}
