package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(): UserDatabaseProvider {
        return UserDatabaseProvider(ApplicationProvider.getApplicationContext(), userId, UserDBSecret("db_secret".toByteArray()))
    }
}
