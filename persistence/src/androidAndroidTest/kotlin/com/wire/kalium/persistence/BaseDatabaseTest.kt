package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MockSettings
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.util.FileNameUtil

actual open class BaseDatabaseTest actual constructor() {
    private val userId = UserIDEntity("user_12_id", "user_12_domain")
    private val preferences = KaliumPreferencesSettings(MockSettings())

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(): Database {
        return Database(ApplicationProvider.getApplicationContext(), userId, preferences)
    }

}
