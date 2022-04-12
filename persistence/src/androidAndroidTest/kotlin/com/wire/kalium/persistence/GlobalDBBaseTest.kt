package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.russhwolf.settings.MockSettings
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.util.FileNameUtil

actual abstract class GlobalDBBaseTest {

    private val preferences = KaliumPreferencesSettings(MockSettings())

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.globalDBName())
    }

    actual fun createDatabase(): GlobalDatabaseProvider = GlobalDatabaseProvider(ApplicationProvider.getApplicationContext(), preferences)

}
