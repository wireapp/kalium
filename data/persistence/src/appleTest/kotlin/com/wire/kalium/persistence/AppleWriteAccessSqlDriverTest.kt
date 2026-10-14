/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.util.AccountResourceAccess
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleWriteAccessSqlDriverTest {
    @Test
    fun openReadersSeeOtherConnectionWritesWhileAppWritesWait() = runTest {
        val root = NSTemporaryDirectory() + "wire-write-access-" + NSUUID().UUIDString
        val user = UserIDEntity("reader", "example.com")
        val database = userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked(root)), user, UserDBSecret(TestDatabaseFile.KEY), testScheduler.let {
            kotlinx.coroutines.test.StandardTestDispatcher(it)
        }, enableWAL = true, dbInvalidationControlEnabled = false)
        try {
            val native = database.sqlDriver
            native.execute(null, "CREATE TABLE AccessTest (value INTEGER NOT NULL)", 0).await()
            native.execute(null, "INSERT INTO AccessTest VALUES (1)", 0).await()
            val writes = AccountResourceAccess()
            val app = AppleWriteAccessSqlDriver(native, writes)
            val extension = existingUserDatabaseBuilder(root, user, kotlinx.coroutines.test.StandardTestDispatcher(testScheduler), UserDBSecret(TestDatabaseFile.KEY))
            try {
                writes.pause {}
                val pending = async { app.execute(null, "UPDATE AccessTest SET value = 3", 0).await() }
                runCurrent()
                assertFalse(pending.isCompleted)
                assertEquals(1, app.readValue())
                extension.sqlDriver.execute(null, "UPDATE AccessTest SET value = 2", 0).await()
                assertEquals(2, app.readValue())
                writes.resume {}
                pending.await()
                assertEquals(3, app.readValue())
            } finally {
                extension.close()
            }
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun handoffWaitsForOuterTransactionAndNestedWritesCanFinish() = runTest {
        val root = NSTemporaryDirectory() + "wire-write-transaction-" + NSUUID().UUIDString
        val user = UserIDEntity("transaction", "example.com")
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val database = userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked(root)), user, UserDBSecret(TestDatabaseFile.KEY), dispatcher,
            enableWAL = true, dbInvalidationControlEnabled = false)
        try {
            database.sqlDriver.execute(null, "CREATE TABLE AccessTest (value INTEGER NOT NULL)", 0).await()
            database.sqlDriver.execute(null, "INSERT INTO AccessTest VALUES (1)", 0).await()
            val writes = AccountResourceAccess()
            val app = AppleWriteAccessSqlDriver(database.sqlDriver, writes)
            val transacter = object : SuspendingTransacterImpl(app) {}
            var handedOff = false
            transacter.transaction {
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { writes.pause { handedOff = true } }
                assertFalse(handedOff)
                transacter.transaction { app.execute(null, "UPDATE AccessTest SET value = 2", 0).await() }
                assertFalse(handedOff)
            }
            runCurrent()
            assertTrue(handedOff)
            assertEquals(2, app.readValue())
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    private suspend fun SqlDriver.readValue(): Long = executeQuery(null, "SELECT value FROM AccessTest", {
        check(it.next().value)
        QueryResult.Value(checkNotNull(it.getLong(0)))
    }, 0).await()
}
