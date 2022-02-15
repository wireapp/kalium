package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MockSettings
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

actual open class BaseDatabaseTest actual constructor() {
    private val name: String = "test.db"
    private val preferences = KaliumPreferencesSettings(MockSettings())

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    actual fun createDatabase(): Database {
        return Database(ApplicationProvider.getApplicationContext(), name, preferences)
    }

}
