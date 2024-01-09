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

import co.touchlab.sqliter.DatabaseFileContext.databasePath
import co.touchlab.sqliter.DatabaseFileContext.deleteDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()
    actual val encryptedDBSecret = UserDBSecret(ByteArray(0))

    private var storePath = NSFileManager.defaultManager.URLForDirectory(NSCachesDirectory, NSUserDomainMask, null, true, null)!!.path!!

    actual fun databasePath(
        userId: UserIDEntity
    ): String {
        return databasePath(FileNameUtil.userDBName(userId), storePath)
    }

    actual fun doesDatabaseExist(
        userId: UserIDEntity
    ): Boolean = TODO("not implemented")

    actual fun deleteDatabase(userId: UserIDEntity) {
        deleteDatabase(FileNameUtil.userDBName(userId), storePath)
    }

    actual fun createDatabase(
        userId: UserIDEntity,
        passphrase: UserDBSecret?,
        enableWAL: Boolean
    ): UserDatabaseBuilder {
        return userDatabaseBuilder(platformDBData(userId), userId, null, dispatcher, false)
    }

    actual fun platformDBData(userId: UserIDEntity): PlatformDatabaseData = PlatformDatabaseData(storePath)
}
