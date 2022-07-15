package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(): UserDatabaseProvider {
        val rootStoragePath = "Users/me/storage"
        val rootCachePath = "Users/me/cache"
        val storageFile = File(rootStoragePath)
        val cacheFile = File(rootCachePath)
        return UserDatabaseProvider(
            ApplicationProvider.getApplicationContext(),
            userId,
            storageFile,
            cacheFile,
            UserDBSecret("db_secret".toByteArray())
        )
    }
}
