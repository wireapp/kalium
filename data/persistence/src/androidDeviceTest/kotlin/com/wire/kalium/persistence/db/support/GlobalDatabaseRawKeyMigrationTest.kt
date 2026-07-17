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

package com.wire.kalium.persistence.db.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobalDatabaseRawKeyMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseFile: File
        get() = context.getDatabasePath(DATABASE_NAME)

    @BeforeTest
    fun setUp() {
        System.loadLibrary("sqlcipher")
        deleteDatabase()
    }

    @AfterTest
    fun tearDown() {
        deleteDatabase()
    }

    @Test
    fun given32ByteSecret_whenConvertingToRawKey_thenSqlCipherBlobFormatIsReturned() {
        val rawKey = SECRET.toSqlCipherRawKey()

        assertEquals(67, rawKey.size)
        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'",
            rawKey.decodeToString()
        )
    }

    @Test
    fun givenHistoricalGlobalKey_whenMigrating_thenDatabaseUsesFreshRawKey() {
        val rawKey = V2_SECRET.toSqlCipherRawKey()
        var migrationCompleted = false
        createDatabase(HISTORICAL_V1_SECRET)

        assertContentEquals(
            rawKey,
            globalDatabaseKey(databaseFile, HISTORICAL_V1_SECRET, rawKey) { migrationCompleted = true }
        )
        assertTrue(migrationCompleted)
        assertTrue(canOpenDatabase(databaseFile, rawKey, verifyIntegrity = true))
        assertFalse(canOpenDatabase(databaseFile, HISTORICAL_V1_SECRET))
    }

    @Test
    fun givenRekeyCompletedBeforeV2AliasWasStored_whenMigratingAgain_thenRawKeyIsRecovered() {
        val rawKey = V2_SECRET.toSqlCipherRawKey()
        var migrationCompleted = false
        createDatabase(rawKey)

        assertContentEquals(
            rawKey,
            globalDatabaseKey(databaseFile, HISTORICAL_V1_SECRET, rawKey) { migrationCompleted = true }
        )
        assertTrue(migrationCompleted)
        assertTrue(canOpenDatabase(databaseFile, rawKey, verifyIntegrity = true))
    }

    private fun createDatabase(key: ByteArray) {
        databaseFile.parentFile?.mkdirs()
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            key,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            null
        )
        try {
            database.execSQL("CREATE TABLE migration_test(id INTEGER PRIMARY KEY)")
        } finally {
            database.close()
        }
    }

    private fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    private companion object {
        const val DATABASE_NAME = "sqlcipher-global-key-test.db"
        val SECRET = ByteArray(32) { index -> index.toByte() }
        val V2_SECRET = ByteArray(32) { index -> (index + 1).toByte() }
        val HISTORICAL_V1_SECRET = "historical-v1-secret".encodeToByteArray()
    }
}
