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

import co.touchlab.sqliter.DatabaseFileContext
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.clearInMemoryDatabase
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.db.inMemoryDatabase
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.uuid.Uuid

// Named in-memory databases share data, so a database left open by a failed close would leak rows into the next test for that user.
private val testDatabaseNames = mutableMapOf<UserIDEntity, String>()

internal actual fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseBuilder {
    val databaseName = "${FileNameUtil.userDBName(userId)}-${Uuid.random()}"
    testDatabaseNames[userId] = databaseName
    return inMemoryDatabase(userId, dispatcher, databaseName)
}

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    testDatabaseNames.remove(userId)?.let { clearInMemoryDatabase(databaseName = it) }
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseBuilder {
    return globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(getTempDatabaseDirectory())),
        passphrase = null,
        queriesContext = StandardTestDispatcher()
    )
}

internal actual fun deleteTestGlobalDatabase() {
    DatabaseFileContext.deleteDatabase(FileNameUtil.globalDBName(), getTempDatabaseDirectory())
}

private fun getTempDatabaseDirectory() = NSFileManager.defaultManager().URLForDirectory(
    NSCachesDirectory,
    NSUserDomainMask,
    null,
    true,
    null
)!!.path!!
