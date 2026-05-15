/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.benchmarks.android

import android.content.Context
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.Dispatchers

/**
 * Builds a SQLCipher-encrypted user database matching the production Android path
 * (`userDatabaseBuilder` → `SupportOpenHelperFactory` → libsqlcipher).
 *
 * The passphrase mirrors the one used by `BaseDatabaseTest` in `:data:persistence`.
 */
internal object BenchmarkDb {
    val selfUserId: UserIDEntity = UserIDEntity("benchmark-self", "benchmark.wire.com")

    private val passphrase = UserDBSecret("db_secret".toByteArray())

    fun build(context: Context): UserDatabaseBuilder = userDatabaseBuilder(
        platformDatabaseData = PlatformDatabaseData(context),
        userId = selfUserId,
        passphrase = passphrase,
        dispatcher = Dispatchers.IO,
        enableWAL = true
    )

    fun delete(context: Context) {
        context.deleteDatabase(FileNameUtil.userDBName(selfUserId))
    }
}
