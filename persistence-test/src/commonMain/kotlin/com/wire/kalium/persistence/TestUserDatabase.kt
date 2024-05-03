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

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

class TestUserDatabase(
    val userId: UserIDEntity,
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) {

    val builder: UserDatabaseBuilder

    init {
        deleteTestDatabase(userId)
        builder = createTestDatabase(userId, dispatcher)
    }

    fun delete() {
        deleteTestDatabase(userId)
    }
}

internal fun getTempDatabaseFileNameForUser(userId: UserIDEntity) = "TEMP-TEST-DB-${userId.value}.${userId.domain}.db"

internal expect fun deleteTestDatabase(userId: UserIDEntity)

internal expect fun createTestDatabase(userId: UserIDEntity, dispatcher: TestDispatcher): UserDatabaseBuilder
