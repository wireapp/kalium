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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

class TestGlobalDatabase(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) {

    val provider: GlobalDatabaseBuilder

    init {
        deleteTestGlobalDatabase()
        provider = createTestGlobalDatabase()
    }

    fun delete() {
        deleteTestGlobalDatabase()
    }
}

internal expect fun deleteTestGlobalDatabase()

internal expect fun createTestGlobalDatabase(): GlobalDatabaseBuilder
