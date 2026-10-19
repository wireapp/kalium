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
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.db.userDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher

private val userDatabases = mutableMapOf<UserIDEntity, UserDatabaseBuilder>()
private var globalDatabase: GlobalDatabaseBuilder? = null

internal actual fun createTestDatabase(
    userId: UserIDEntity,
    dispatcher: TestDispatcher
): UserDatabaseBuilder = userDatabaseBuilder(
    platformDatabaseData = PlatformDatabaseData(),
    userId = userId,
    passphrase = null,
    dispatcher = dispatcher,
    enableWAL = false,
).also { userDatabases[userId] = it }

internal actual fun deleteTestDatabase(userId: UserIDEntity) {
    userDatabases.remove(userId)?.close()
}

internal actual fun createTestGlobalDatabase(): GlobalDatabaseBuilder = globalDatabaseProvider(
    platformDatabaseData = PlatformDatabaseData(),
    queriesContext = Dispatchers.Default,
    passphrase = null,
    enableWAL = false,
).also { globalDatabase = it }

internal actual fun deleteTestGlobalDatabase() {
    globalDatabase?.close()
    globalDatabase = null
}
