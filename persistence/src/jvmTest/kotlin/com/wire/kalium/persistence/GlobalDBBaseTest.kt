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

import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.nio.file.Files

actual abstract class GlobalDBBaseTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    val databaseFile = Files.createTempDirectory("test-storage").toFile().resolve("test-kalium.db")

    actual fun deleteDatabase() {
        databaseFile.delete()
    }

    actual fun createDatabase(platformDatabaseData: PlatformDatabaseData): GlobalDatabaseBuilder {
        return globalDatabaseProvider(
            platformDatabaseData = platformDatabaseData,
            queriesContext = dispatcher,
            passphrase = null,
            enableWAL = false
        )
    }
}
