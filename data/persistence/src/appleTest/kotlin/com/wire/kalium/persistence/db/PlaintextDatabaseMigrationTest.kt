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
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.withConnection
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.MARKER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.NAME
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun givenAnEncryptedDatabaseWithAPlaintextHeader_whenEncryptionIsChecked_thenTheFileIsLeftAsItIs() {
        writeEncryptedDatabaseWithPlaintextHeader()
        val before = database.bytes()
        assertTrue(before.startsWith(SQLITE_HEADER))

        encryptPlaintextDatabase(database.directory, NAME, KEY.decodeToString())

        assertContentEquals(before, database.bytes())
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("${database.directory}/$NAME$ENCRYPTED_COPY_SUFFIX"))
    }

    @Test
    fun givenAnUnencryptedDatabase_whenSeveralThreadsEncryptItAtOnce_thenItIsEncryptedAndKeepsItsContent() {
        database.write(null)

        runBlocking {
            repeat(CONCURRENT_ENCRYPTIONS) {
                launch(Dispatchers.Default) { encryptPlaintextDatabase(database.directory, NAME, KEY.decodeToString()) }
            }
        }

        assertEquals(MARKER, database.read(KEY))
        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath("${database.directory}/$NAME$ENCRYPTED_COPY_SUFFIX"))
    }

    /** An encrypted database that keeps its first 32 bytes readable, as SQLCipher does for iOS app group containers. */
    private fun writeEncryptedDatabaseWithPlaintextHeader() {
        val configuration = DatabaseConfiguration(
            name = NAME,
            version = 1,
            create = { connection ->
                connection.rawExecSql("CREATE TABLE t(x TEXT)")
                connection.rawExecSql("INSERT INTO t VALUES ('$MARKER')")
            },
            journalMode = JournalMode.DELETE,
            extendedConfig = DatabaseConfiguration.Extended(basePath = database.directory),
            lifecycleConfig = DatabaseConfiguration.Lifecycle(
                onCreateConnection = { connection ->
                    connection.setSqlCipherKey(KEY.decodeToString())
                    connection.rawExecSql("PRAGMA cipher_plaintext_header_size = 32")
                    // With a plaintext header the salt isn't stored in the file, so it's given here.
                    connection.rawExecSql("PRAGMA cipher_salt = \"x'$SALT'\"")
                }
            )
        )
        createDatabaseManager(configuration).withConnection { }
    }

    private companion object {
        const val CONCURRENT_ENCRYPTIONS = 4
        const val SALT = "00112233445566778899aabbccddeeff"
    }
}
