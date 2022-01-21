package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.db.Database

actual open class BaseDatabaseTest actual constructor() {
    private val name: String = "test.db"

    actual fun deleteDatabase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    actual fun createDatabase(): Database {
        return Database(ApplicationProvider.getApplicationContext(), name, "123456789")
    }

}
