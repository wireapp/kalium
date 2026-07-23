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

package com.wire.kalium.persistence.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.Dispatchers
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDatabaseRawKeyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun givenRawKey_whenBuildingNewUserDatabase_thenRawKeyReopensDatabase() {
        System.loadLibrary("sqlcipher")
        val userId = UserIDEntity("sqlcipher-raw-key-user", "example.com")
        val databaseName = FileNameUtil.userDBName(userId)
        val databaseFile = context.getDatabasePath(databaseName)
        context.deleteDatabase(databaseName)

        val database = userDatabaseBuilder(
            platformDatabaseData = PlatformDatabaseData(context),
            userId = userId,
            passphrase = UserDBSecret(RAW_KEY),
            dispatcher = Dispatchers.IO,
            enableWAL = true,
            dbInvalidationControlEnabled = false
        )
        try {
            assertTrue(canOpenDatabase(databaseFile, RAW_KEY))
            assertFalse(canOpenDatabase(databaseFile, LEGACY_KEY))
        } finally {
            database.nuke()
        }
    }

    private fun canOpenDatabase(databaseFile: File, key: ByteArray): Boolean {
        var database: SQLiteDatabase? = null
        return try {
            database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                key,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )
            database.rawQuery("SELECT COUNT(*) FROM sqlite_schema", emptyArray()).use { it.moveToFirst() }
        } catch (_: SQLiteException) {
            false
        } finally {
            database?.close()
        }
    }

    private companion object {
        val LEGACY_KEY = ByteArray(32) { index -> index.toByte() }
        val RAW_KEY = "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'".encodeToByteArray()
    }
}
