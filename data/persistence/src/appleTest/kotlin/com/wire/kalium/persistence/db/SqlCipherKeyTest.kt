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
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.RAW_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SALT
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.withSalt
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
    fun givenAKeyWithoutASaltOnIos_whenCreatingTheDriver_thenItFailsBeforeCreatingTheDatabase() {
        if (!isPlaintextHeaderUsed) return

        assertFails { database.driver(RAW_KEY.encodeToByteArray()) }
        assertFails { database.driver("a passphrase".encodeToByteArray()) }
        assertFails { database.driver(withSalt(RAW_KEY, "zz".repeat(16)).encodeToByteArray()) }
        assertEquals(0, database.bytes().size)
    }

    @Test
    fun givenAKey_whenWritingTheDatabase_thenItIsEncryptedAndOpensWithTheKey() {
        database.write(KEY)

        // On iOS behind a readable header, on macOS entirely.
        assertEquals(isPlaintextHeaderUsed, database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.bytes().contains(MARKER))
        assertEquals(MARKER, database.read(KEY))
    }

    @Test
    fun givenAnOpenWalDatabaseOnIos_whenWriting_thenItsHeaderIdentifiesItAsSqliteInWalModeAndNothingElseIsReadable() {
        if (!isPlaintextHeaderUsed) return
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
    fun givenAnEncryptedDatabaseOnIos_whenOpeningItWithoutItsSaltOrTheReadableHeader_thenItFails() {
        if (!isPlaintextHeaderUsed) return
        database.write(KEY)

        readWith(withSalt(RAW_KEY, SALT), plaintextHeader = true)
        assertFails { readWith(RAW_KEY, plaintextHeader = true) }
        assertFails { readWith(withSalt(RAW_KEY, OTHER_SALT), plaintextHeader = true) }
        assertFails { readWith(withSalt(RAW_KEY, SALT), plaintextHeader = false) }
    }

    @Test
    fun givenAnEncryptedDatabaseOnMacOs_whenOpeningIt_thenItHasSqlCipherDefaultFormat() {
        if (isPlaintextHeaderUsed) return
        database.write(KEY)

        readWith(RAW_KEY, plaintextHeader = false)
        assertFails { readWith(withSalt(RAW_KEY, SALT), plaintextHeader = true) }
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
        val OTHER_SALT = "a5".repeat(16)
    }
}
