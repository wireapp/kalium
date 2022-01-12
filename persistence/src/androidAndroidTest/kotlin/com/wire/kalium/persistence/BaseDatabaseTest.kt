package com.wire.kalium.persistence

import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.db.DatabaseDriverFactory

actual open class BaseDatabaseTest actual constructor() {

    actual fun createDatabaseDriverFactory(): DatabaseDriverFactory {
        return DatabaseDriverFactory(ApplicationProvider.getApplicationContext(), "123456789")
    }

}
