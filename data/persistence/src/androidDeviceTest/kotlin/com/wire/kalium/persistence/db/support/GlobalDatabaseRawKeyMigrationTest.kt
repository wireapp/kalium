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
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun givenHistoricalGlobalKey_whenMigrating_thenDatabaseUsesFreshRawKey() {
        var migrated = false
        createDatabase(HISTORICAL_V1_SECRET)

        assertContentEquals(
            RAW_KEY,
            globalDatabaseKey(databaseFile, RAW_KEY, HISTORICAL_V1_SECRET) { migrated = true }
        )
        assertTrue(migrated)
        assertTrue(canOpenDatabase(databaseFile, RAW_KEY, verifyIntegrity = true))
        assertFalse(canOpenDatabase(databaseFile, HISTORICAL_V1_SECRET))
    }

    @Test
    fun givenRekeyCompletedBeforeLegacyAliasWasCleared_whenStartingAgain_thenTheDatabaseIsNotRekeyedTwice() {
        var migrated = false
        var rekeyCalls = 0
        createDatabase(RAW_KEY)

        assertContentEquals(
            RAW_KEY,
            globalDatabaseKey(
                databaseFile = databaseFile,
                rawKey = RAW_KEY,
                legacyKey = HISTORICAL_V1_SECRET,
                rekey = { _, _, _ -> rekeyCalls++ },
                onMigrated = { migrated = true }
            )
        )
        assertEquals(0, rekeyCalls)
        assertTrue(migrated)
        assertTrue(canOpenDatabase(databaseFile, RAW_KEY, verifyIntegrity = true))
    }

    @Test
    fun givenRekeyFailsAndLegacyKeyStillWorks_whenMigrating_thenLegacyKeyIsKeptAndMigrationIsNotCompleted() {
        var migrated = false
        createDatabase(HISTORICAL_V1_SECRET)

        val selectedKey = globalDatabaseKey(
            databaseFile = databaseFile,
            rawKey = RAW_KEY,
            legacyKey = HISTORICAL_V1_SECRET,
            rekey = { _, _, _ -> throw IllegalStateException("rekey failed") },
            onMigrated = { migrated = true }
        )

        assertContentEquals(HISTORICAL_V1_SECRET, selectedKey)
        assertFalse(migrated)
        assertTrue(canOpenDatabase(databaseFile, HISTORICAL_V1_SECRET, verifyIntegrity = true))
    }

    @Test
    fun givenNeitherKeyOpensTheDatabase_whenMigrating_thenTheMigrationFailureIsPropagated() {
        var migrated = false
        createDatabase(HISTORICAL_V1_SECRET)

        assertFailsWith<SQLiteException> {
            globalDatabaseKey(
                databaseFile = databaseFile,
                rawKey = RAW_KEY,
                legacyKey = "wrong-legacy-secret".encodeToByteArray(),
                onMigrated = { migrated = true }
            )
        }
        assertFalse(migrated)
    }

    @Test
    fun givenNoLegacyKey_whenSelectingTheKey_thenTheRawKeyIsUsedWithoutTouchingTheDatabase() {
        var migrated = false
        var rekeyCalls = 0
        createDatabase(RAW_KEY)

        assertContentEquals(
            RAW_KEY,
            globalDatabaseKey(
                databaseFile = databaseFile,
                rawKey = RAW_KEY,
                legacyKey = null,
                rekey = { _, _, _ -> rekeyCalls++ },
                onMigrated = { migrated = true }
            )
        )
        assertEquals(0, rekeyCalls)
        assertFalse(migrated)
    }

    @Test
    fun givenAnEmptyDatabaseFile_whenSelectingTheKey_thenItIsNotTreatedAsAnExistingDatabase() {
        var rekeyCalls = 0
        databaseFile.parentFile?.mkdirs()
        databaseFile.createNewFile()

        assertContentEquals(
            RAW_KEY,
            globalDatabaseKey(
                databaseFile = databaseFile,
                rawKey = RAW_KEY,
                legacyKey = HISTORICAL_V1_SECRET,
                rekey = { _, _, _ -> rekeyCalls++ },
                onMigrated = { }
            )
        )
        assertEquals(0, rekeyCalls)
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

        /** SQLCipher raw-key literal. The encoder that produces this is covered by `SecurityHelperTest`. */
        val RAW_KEY = "x'0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20'".encodeToByteArray()
        val HISTORICAL_V1_SECRET = "historical-v1-secret".encodeToByteArray()
    }
}
