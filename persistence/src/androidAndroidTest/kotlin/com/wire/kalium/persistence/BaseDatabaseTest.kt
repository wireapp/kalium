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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

actual open class BaseDatabaseTest actual constructor() {

    protected actual val dispatcher: TestDispatcher = StandardTestDispatcher()
    actual val encryptedDBSecret: UserDBSecret = UserDBSecret(ByteArray(0))

    actual fun deleteDatabase(userId: UserIDEntity) {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FileNameUtil.userDBName(userId))
    }

    actual fun createDatabase(userId: UserIDEntity): UserDatabaseBuilder {
        return userDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            userId = userId,
            encrypt = true,
            passphrase = encryptedDBSecret,
            dispatcher = dispatcher
        )
    }

    actual fun databasePath(userId: UserIDEntity): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        return context.getDatabasePath(FileNameUtil.userDBName(userId)).absolutePath
    }

}
