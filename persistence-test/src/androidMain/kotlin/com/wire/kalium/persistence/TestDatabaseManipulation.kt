/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.GlobalDatabaseSecret
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.sqliteGlobalDatabaseProvider
import com.wire.kalium.persistence.db.inMemoryDatabase
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

internal actual fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseBuilder {
    return inMemoryDatabase(
        ApplicationProvider.getApplicationContext(),
        userId,
        dispatcher = dispatcher
    )
}

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    val context: Context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(getTempDatabaseFileNameForUser(userId))
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseBuilder {
    return sqliteGlobalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(ApplicationProvider.getApplicationContext()),
        passphrase = GlobalDatabaseSecret("test_db_secret".toByteArray()),
        enableWAL = true,
        queriesContext = StandardTestDispatcher()
    )
}

internal actual fun deleteTestGlobalDatabase() {
    val context: Context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(FileNameUtil.globalDBName())
}
