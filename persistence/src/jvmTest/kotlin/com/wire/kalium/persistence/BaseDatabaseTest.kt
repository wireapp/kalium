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
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()
    actual val encryptedDBSecret = UserDBSecret("db_secret".toByteArray())

    val UserIDEntity.databaseFile
        get() = Files.createTempDirectory("test-storage").toFile().resolve("test-$domain-$value.db")

    actual fun databasePath(
        userId: UserIDEntity
    ): String {
        return userId.databaseFile.path
    }

    actual fun deleteDatabase(userId: UserIDEntity) {
        userId.databaseFile.delete()
    }

    actual fun doesDatabaseExist(userId: UserIDEntity): Boolean {
        return databasePath(userId).let { File(it).exists() }
    }

    actual fun createDatabase(
        userId: UserIDEntity,
        passphrase: UserDBSecret?,
        enableWAL: Boolean
    ): UserDatabaseBuilder {
        return userDatabaseBuilder(
            platformDatabaseData = PlatformDatabaseData(userId.databaseFile),
            userId = userId,
            passphrase = null,
            dispatcher = dispatcher,
            enableWAL = enableWAL
        )
    }
}
