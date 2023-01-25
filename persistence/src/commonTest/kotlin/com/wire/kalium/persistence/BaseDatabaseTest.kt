/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.TestDispatcher

expect open class BaseDatabaseTest() {

    protected val dispatcher: TestDispatcher
    val encryptedDBSecret: UserDBSecret

    fun databasePath(
        userId: UserIDEntity = DefaultDatabaseTestValues.userId
    ): String

    fun deleteDatabase(
        userId: UserIDEntity = DefaultDatabaseTestValues.userId
    )

    fun createDatabase(
        userId: UserIDEntity = DefaultDatabaseTestValues.userId
    ): UserDatabaseBuilder

}

object DefaultDatabaseTestValues {
    val userId = UserIDEntity("78dd6502-ab84-40f7-a8b3-1e7e1eb4cc8c", "user_12_domain")
}
