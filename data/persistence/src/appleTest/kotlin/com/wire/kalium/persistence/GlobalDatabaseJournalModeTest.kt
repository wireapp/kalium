/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import co.touchlab.sqliter.DatabaseFileContext.databasePath
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import kotlin.test.Test
import kotlin.test.assertTrue

class GlobalDatabaseJournalModeTest {

    @Test
    fun `global database enables WAL when requested`() {
        val storePath = NSFileManager.defaultManager
            .URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)!!
            .path!!
            .let { "$it/kalium-global-wal-test-${NSUUID.UUID().UUIDString}" }
        val database = globalDatabaseProvider(
            platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(storePath)),
            queriesContext = StandardTestDispatcher(),
            passphrase = null,
            enableWAL = true
        )

        try {
            val databaseFile = databasePath(FileNameUtil.globalDBName(), storePath)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath("$databaseFile-wal"))
        } finally {
            database.nuke()
        }
    }
}
