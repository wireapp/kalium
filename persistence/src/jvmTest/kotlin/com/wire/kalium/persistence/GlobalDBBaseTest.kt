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

import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.nio.file.Files

actual abstract class GlobalDBBaseTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val databaseFile = Files.createTempDirectory("test-storage").toFile().resolve("test-kalium.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(): GlobalDatabaseProvider {
        return globalDatabaseProvider(
            platformDatabaseData = PlatformDatabaseData(
                StorageData.FileBacked(databaseFile)
            ),
            queriesContext = dispatcher,
            passphrase = null,
            enableWAL = false
        )
    }
}
