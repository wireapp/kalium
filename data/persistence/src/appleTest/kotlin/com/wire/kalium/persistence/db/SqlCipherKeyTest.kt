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

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.setCipherKey
import co.touchlab.sqliter.withConnection
import co.touchlab.sqliter.withStatement
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.MARKER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.NAME
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.OTHER_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlCipherKeyTest {

    private val database = TestDatabaseFile()

    @AfterTest
    fun tearDown() = database.delete()

    @Test
    fun givenARawKey_whenAddingItsSalt_thenTheSaltIsDerivedFromTheKey() {
        assertEquals(
            "x'${"42".repeat(32)}f703af143ddbd551644ab5ffc3ab2aaa'",
            sqlCipherKeyWithSalt("x'${"42".repeat(32)}'")
        )
        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fee465d02dddbb2daac1b8ca58494d293'",
            sqlCipherKeyWithSalt("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'")
        )
    }

    @Test
    fun givenSomethingOtherThanARawKey_whenAddingItsSalt_thenItFails() {
        assertFails { sqlCipherKeyWithSalt("a passphrase") }
        assertFails { sqlCipherKeyWithSalt("x'${"42".repeat(31)}'") }
        assertFails { sqlCipherKeyWithSalt("x'${"zz".repeat(32)}'") }
    }

    @Test
    fun givenAKey_whenWritingTheDatabase_thenItIsEncryptedBehindAReadableHeaderAndOpensWithTheKey() {
        database.write(KEY)

        assertTrue(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.bytes().contains(MARKER))
        assertEquals(MARKER, database.read(KEY))
    }

    @Test
    fun givenAnOpenWalDatabase_whenWriting_thenItsHeaderIdentifiesItAsSqliteInWalModeAndNothingElseIsReadable() {
        val driver = database.driver(KEY, isWALEnabled = true)
        try {
            repeat(ROWS) { driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0) }

            // What iOS reads to tell a SQLite database in WAL mode, while the connection still holds it.
            val header = database.bytes().copyOf(PLAINTEXT_HEADER_SIZE)
            assertTrue(header.startsWith(SQLITE_HEADER))
            assertEquals(WAL_FORMAT_VERSION, header[FILE_FORMAT_WRITE_VERSION_OFFSET])
            assertEquals(WAL_FORMAT_VERSION, header[FILE_FORMAT_READ_VERSION_OFFSET])
            assertFalse(database.bytes().contains(MARKER))
            assertFalse(database.bytes("-wal").contains(MARKER))
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpeningItWithAnotherKeyOrNone_thenItFails() {
        database.write(KEY)

        assertFails { database.read(OTHER_KEY) }
        assertFails { database.read(null) }
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpeningItWithoutTheSaltOrTheReadableHeader_thenItFails() {
        database.write(KEY)

        assertFails { readWith(KEY.decodeToString(), plaintextHeader = true) }
        assertFails { readWith(sqlCipherKeyWithSalt(KEY.decodeToString()), plaintextHeader = false) }
    }

    @Test
    fun givenAWalDatabase_whenReadingAfterAWrite_thenTheReaderConnectionIsKeyedToo() {
        val driver = database.driver(KEY, isWALEnabled = true)
        try {
            driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0)

            assertEquals(MARKER, driver.firstValue())
        } finally {
            driver.close()
        }
    }

    private fun readWith(key: String, plaintextHeader: Boolean) = createDatabaseManager(
        DatabaseConfiguration(
            name = NAME,
            version = NO_VERSION_CHECK,
            create = {},
            extendedConfig = DatabaseConfiguration.Extended(basePath = database.directory),
            lifecycleConfig = DatabaseConfiguration.Lifecycle(
                onCreateConnection = { connection ->
                    connection.setCipherKey(key)
                    if (plaintextHeader) {
                        connection.rawExecSql("PRAGMA cipher_plaintext_header_size = $PLAINTEXT_HEADER_SIZE")
                    }
                }
            )
        )
    ).withConnection { connection -> connection.withStatement("SELECT count(*) FROM t") { query().next() } }

    private companion object {
        const val ROWS = 50
        const val FILE_FORMAT_WRITE_VERSION_OFFSET = 18
        const val FILE_FORMAT_READ_VERSION_OFFSET = 19
        const val WAL_FORMAT_VERSION: Byte = 2
    }
}
