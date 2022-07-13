package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.util.FileNameUtil

actual abstract class GlobalDBBaseTest {

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.globalDBName())
    }

    actual fun createDatabase(): GlobalDatabaseProvider =
        GlobalDatabaseProvider(ApplicationProvider.getApplicationContext(), GlobalDatabaseSecret("test_db_secret".toByteArray()))

}
