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

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlin.test.AfterTest

actual open class BaseDatabaseTest actual constructor() {
    // Keep worker storage alive across connection facades, as native test databases do.
    private val databases = mutableMapOf<UserIDEntity, UserDatabaseBuilder>()

    @AfterTest
    fun closeWorkerDatabases() {
        databases.values.forEach { it.close() }
        databases.clear()
    }

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()

    actual fun deleteDatabase(userId: UserIDEntity) {
        databases.remove(userId)?.close()
    }

    actual fun createDatabase(
        userId: UserIDEntity,
        passphrase: UserDBSecret?,
        enableWAL: Boolean,
        dbInvalidationControlEnabled: Boolean
    ): UserDatabaseBuilder {
        val storage = databases.getOrPut(userId) {
            com.wire.kalium.persistence.db.userDatabaseBuilder(
                platformDatabaseData = PlatformDatabaseData(),
                userId = userId,
                passphrase = passphrase,
                dispatcher = dispatcher,
                enableWAL = enableWAL,
                dbInvalidationControlEnabled = dbInvalidationControlEnabled
            )
        }
        return UserDatabaseBuilder(
            userId = userId,
            sqlDriver = object : SqlDriver by storage.sqlDriver {
                // Closing a connection must not discard the named test database.
                override fun close() = Unit
            },
            dispatcher = dispatcher,
            platformDatabaseData = PlatformDatabaseData(),
            isEncrypted = false,
            dbInvalidationController = storage.dbInvalidationController,
            initializeSelfUser = false,
        )
    }

    actual fun databasePath(
        userId: UserIDEntity
    ): String {
        return userId.toString()
    }

    actual fun doesDatabaseExist(
        userId: UserIDEntity
    ): Boolean = true

    actual val encryptedDBSecret: UserDBSecret
        get() = UserDBSecret(ByteArray(0))

    actual fun platformDBData(userId: UserIDEntity): PlatformDatabaseData {
        return PlatformDatabaseData()
    }
}
