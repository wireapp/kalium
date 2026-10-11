/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.sqliter.JournalMode
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fgetc
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fseek
import platform.posix.SEEK_SET
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleExistingDatabaseTest {
    private val dispatcher = StandardTestDispatcher()
    private val userId = UserIDEntity("test-user", "example.com")
    private val fileManager = NSFileManager.defaultManager

    @Test
    fun `missing storage is not created`() = withDirectory { root ->
        val missing = "$root/missing"
        val failure = assertFailsWith<ExistingDatabaseException> { existingUserDatabaseBuilder(missing, userId, dispatcher) }
        assertFalse(failure.cleanupUncertain)
        assertFalse(fileManager.fileExistsAtPath(missing))
    }

    @Test
    fun `missing main database is not created`() = withDirectory { root ->
        val failure = assertFailsWith<ExistingDatabaseException> { existingUserDatabaseBuilder(root, userId, dispatcher) }
        assertFalse(failure.cleanupUncertain)
        assertFalse(fileManager.fileExistsAtPath(databasePath(root)))
    }

    @Test
    fun `empty or corrupt main database is rejected without repair`() = withDirectory { root ->
        for (content in listOf("", "not a SQLite database")) {
            val file = checkNotNull(fopen(databasePath(root), "w"))
            fputs(content, file)
            fclose(file)
            val failure = assertFailsWith<ExistingDatabaseException> { existingUserDatabaseBuilder(root, userId, dispatcher) }
            assertFalse(failure.cleanupUncertain)
        }
    }

    @Test
    fun `older schema remains unchanged`() = rejectedVersion(-1)

    @Test
    fun `newer schema remains unchanged`() = rejectedVersion(1)

    @Test
    fun `schema committed only in WAL is checked`() = withDirectory { root ->
        val prepared = prepareUser(root)
        try {
            val version = UserDatabase.Schema.version + 1
            prepared.sqlDriver.execute(null, "PRAGMA user_version = $version", 0)
            assertTrue(fileManager.fileExistsAtPath("${databasePath(root)}-wal"))
            assertTrue(mainFileVersion(databasePath(root)) != version, "Changed schema version must only be in WAL")
            val failure = assertFailsWith<ExistingDatabaseException> { existingUserDatabaseBuilder(root, userId, dispatcher) }
            assertEquals(ExistingDatabaseException.Reason.INCOMPATIBLE, failure.reason)
            assertFalse(failure.cleanupUncertain)
            assertEquals(version, prepared.sqlDriver.readLong("PRAGMA user_version"))
        } finally {
            prepared.close()
        }
    }

    @Test
    fun `existing open does not insert self user and supports close reopen`() = withDirectory { root ->
        val prepared = prepareUser(root)
        prepared.sqlDriver.execute(null, "DELETE FROM SelfUser", 0)
        prepared.close()
        repeat(2) {
            val existing = existingUserDatabaseBuilder(root, userId, dispatcher)
            try {
                assertEquals(0L, existing.sqlDriver.readLong("SELECT count(*) FROM SelfUser"))
                assertEquals(UserDatabase.Schema.version, existing.sqlDriver.readLong("PRAGMA user_version"))
            } finally {
                existing.close()
                existing.close()
            }
        }
    }

    @Test
    fun `file removed before native open stays missing and cleanup is uncertain`() = withDirectory { root ->
        prepareUser(root).close()
        assertTrue(fileManager.removeItemAtPath(databasePath(root), null))
        val failure = assertFailsWith<ExistingDatabaseException> {
            openExistingNativeDriver(root, FileNameUtil.userDBName(userId), UserDatabase.Schema.version, JournalMode.WAL)
        }
        assertTrue(failure.cleanupUncertain)
        assertFalse(fileManager.fileExistsAtPath(databasePath(root)))
    }

    @Test
    fun `failed driver close cannot become successful on retry`() = withDirectory { root ->
        val prepared = prepareUser(root)
        val driver = CloseFailsOnceDriver(prepared.sqlDriver)
        val existing = UserDatabaseBuilder(
            userId, driver, dispatcher, PlatformDatabaseData(StorageData.FileBacked(root)), false,
            DbInvalidationController(false) {}, initializeSelfUser = false,
        )
        try {
            repeat(2) { assertFailsWith<IllegalStateException> { existing.close() } }
            assertEquals(1, driver.closeCalls)
        } finally {
            prepared.close()
        }
    }

    @Test
    fun `later native reader creation failure remains uncertain after recovery`() = withDirectory { root ->
        val prepared = prepareUser(root)
        val moved = "$root-moved"
        assertTrue(fileManager.moveItemAtPath(root, moved, null))
        try {
            assertFailsWith<Exception> { prepared.sqlDriver.readLong("SELECT count(*) FROM SelfUser") }
        } finally {
            assertTrue(fileManager.moveItemAtPath(moved, root, null))
        }
        assertEquals(1L, prepared.sqlDriver.readLong("SELECT count(*) FROM SelfUser"))
        repeat(2) { assertFailsWith<IllegalStateException> { prepared.close() } }
    }

    @Test
    fun `ordinary SQL failure does not make native cleanup uncertain`() = withDirectory { root ->
        val prepared = prepareUser(root)
        assertFailsWith<Exception> { prepared.sqlDriver.execute(null, "INSERT INTO TableThatDoesNotExist VALUES (1)", 0) }
        assertEquals(1L, prepared.sqlDriver.readLong("SELECT count(*) FROM SelfUser"))
        prepared.close()
        existingUserDatabaseBuilder(root, userId, dispatcher).close()
    }

    private fun rejectedVersion(delta: Long) = withDirectory { root ->
        val prepared = prepareUser(root)
        val version = UserDatabase.Schema.version + delta
        prepared.sqlDriver.execute(null, "PRAGMA user_version = $version", 0)
        prepared.close()
        val failure = assertFailsWith<ExistingDatabaseException> { existingUserDatabaseBuilder(root, userId, dispatcher) }
        assertEquals(ExistingDatabaseException.Reason.INCOMPATIBLE, failure.reason)
        assertFalse(failure.cleanupUncertain)
        // Opening at the deliberately changed version verifies that no migration changed it back.
        val driver = openExistingNativeDriver(root, FileNameUtil.userDBName(userId), version, JournalMode.WAL)
        try {
            assertEquals(version, driver.readLong("PRAGMA user_version"))
            assertEquals(1L, driver.readLong("SELECT count(*) FROM SelfUser"))
        } finally {
            driver.close()
        }
    }

    private fun prepareUser(root: String): UserDatabaseBuilder = userDatabaseBuilder(
        PlatformDatabaseData(StorageData.FileBacked(root), useGradleSafeSqliterLogging = true),
        userId, null, dispatcher,
    )

    private fun databasePath(root: String) = "$root/${FileNameUtil.userDBName(userId)}"

    private fun mainFileVersion(path: String): Long {
        val file = checkNotNull(fopen(path, "rb"))
        return try {
            check(fseek(file, 60, SEEK_SET) == 0)
            var version = 0L
            repeat(4) { version = (version shl 8) or fgetc(file).toLong() }
            version
        } finally {
            fclose(file)
        }
    }

    private fun withDirectory(block: (String) -> Unit) {
        // Exercise URI escaping as well as ordinary opening in every fixture.
        val root = "${NSTemporaryDirectory()}kalium-existing-${NSUUID.UUID().UUIDString} #?%é"
        assertTrue(fileManager.createDirectoryAtPath(root, true, null, null))
        try {
            block(root)
        } finally {
            assertTrue(fileManager.removeItemAtPath(root, null))
        }
    }

    private fun SqlDriver.readLong(query: String): Long = executeQuery(null, query, { cursor ->
        check(cursor.next().value)
        QueryResult.Value(checkNotNull(cursor.getLong(0)))
    }, 0).value

    private class CloseFailsOnceDriver(delegate: SqlDriver) : SqlDriver by delegate {
        var closeCalls = 0
        override fun close() {
            closeCalls++
            if (closeCalls == 1) error("Native close failed")
        }
    }
}
