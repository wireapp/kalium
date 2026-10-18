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
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.benchmarks.fixtures.BenchmarkEntities
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.Dispatchers
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.rules.ExternalResource

/**
 * Builds a SQLCipher-encrypted user database matching the production Android path
 * (`userDatabaseBuilder` → `SupportOpenHelperFactory` → libsqlcipher).
 *
 * The passphrase is a fixed raw SQLCipher key, the same one `UserDatabaseRawKeyTest` uses.
 */
internal object BenchmarkDb {
    val selfUserId: UserIDEntity = BenchmarkEntities.selfUser.id

    private val passphrase = UserDBSecret(
        "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'".encodeToByteArray()
    )

    fun build(context: Context): UserDatabaseBuilder = userDatabaseBuilder(
        platformDatabaseData = PlatformDatabaseData(context),
        userId = selfUserId,
        passphrase = passphrase,
        dispatcher = Dispatchers.IO,
        enableWAL = true
    )

    /** Setup-only access to this fixture's encrypted database. Never used in timed page loads. */
    fun <T> withSqlCipher(context: Context, block: (SQLiteDatabase) -> T): T {
        val path = context.getDatabasePath(FileNameUtil.userDBName(selfUserId)).absolutePath
        val connection = SQLiteDatabase.openDatabase(path, passphrase.value, null, SQLiteDatabase.OPEN_READWRITE, null)
        return try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    fun delete(context: Context) {
        context.deleteDatabase(FileNameUtil.userDBName(selfUserId))
    }
}

/** Deletes any previous benchmark database, builds a fresh one on first use and nukes it after the test. */
class BenchmarkDbRule : ExternalResource() {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val lazyDb = lazy {
        BenchmarkDb.delete(context)
        BenchmarkDb.build(context)
    }
    val db: UserDatabaseBuilder by lazyDb

    override fun after() {
        if (lazyDb.isInitialized()) db.nuke()
    }
}
