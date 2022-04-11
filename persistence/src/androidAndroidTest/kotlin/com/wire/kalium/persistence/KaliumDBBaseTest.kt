package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MockSettings
import com.wire.kalium.persistence.db.KaliumDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.util.FileNameUtil

actual abstract class KaliumDBBaseTest {

    private val preferences = KaliumPreferencesSettings(MockSettings())

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.appDBName())
    }

    actual fun createDatabase(): KaliumDatabaseProvider = KaliumDatabaseProvider(ApplicationProvider.getApplicationContext(), preferences)

}
