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
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.NAME
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaintextDatabaseMigrationTest {

    private val database = TestDatabaseFile()

    @AfterTest
    fun tearDown() = database.delete()

    // That it opens at all shows that the user version survived: otherwise SQLiter would create the schema again.
    @Test
    fun givenAnUnencryptedWalDatabase_whenOpenedWithAKey_thenItIsEncryptedAndKeepsItsContent() {
        database.write(null, isWALEnabled = true)

        assertEquals(MARKER, database.read(KEY))

        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.bytes().contains(MARKER))
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("${database.directory}/$NAME$ENCRYPTED_COPY_SUFFIX"))
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpenedAgain_thenTheFileIsLeftAsItIs() {
        database.write(KEY)
        val before = database.bytes()

        assertEquals(MARKER, database.read(KEY))

        assertContentEquals(before, database.bytes())
    }

    @Test
    fun givenACopyLeftByAnInterruptedRun_whenOpened_thenTheDatabaseIsStillEncrypted() {
        database.write(null)
        @Suppress("CAST_NEVER_SUCCEEDS")
        ("not a database" as NSString).writeToFile(
            "${database.directory}/$NAME$ENCRYPTED_COPY_SUFFIX",
            true,
            NSUTF8StringEncoding,
            null
        )

        assertEquals(MARKER, database.read(KEY))

        assertFalse(database.bytes().contains(MARKER))
    }
}
