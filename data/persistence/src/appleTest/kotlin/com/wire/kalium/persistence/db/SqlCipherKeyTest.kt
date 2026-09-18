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

import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.MARKER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.OTHER_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class SqlCipherKeyTest {

    private val database = TestDatabaseFile()

    @AfterTest
    fun tearDown() = database.delete()

    @Test
    fun givenAKey_whenWritingTheDatabase_thenTheFileIsEncryptedAndOpensWithTheKey() {
        database.write(KEY)

        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.bytes().contains(MARKER))
        assertEquals(MARKER, database.read(KEY))
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpeningItWithAnotherKeyOrNone_thenItFails() {
        database.write(KEY)

        assertFails { database.read(OTHER_KEY) }
        assertFails { database.read(null) }
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
}
