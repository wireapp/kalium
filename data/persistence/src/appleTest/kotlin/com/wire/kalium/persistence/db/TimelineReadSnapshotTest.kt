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
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineLoadResultEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineRequestEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import com.wire.kalium.util.AccountResourceAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimelineReadSnapshotTest {
    @Test
    fun refreshingPendingPolicyCannotResurrectDeletedOrChangeSentMessages() = runTest {
        val root = NSTemporaryDirectory() + "wire-pending-policy-" + NSUUID().UUIDString
        val user = newUserEntity("sender")
        val conversation = newConversationEntity("policy")
        val database = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(root)), user.id, UserDBSecret(TestDatabaseFile.KEY),
            StandardTestDispatcher(testScheduler), enableWAL = true, dbInvalidationControlEnabled = false,
        )
        try {
            database.userDAO.upsertUsers(listOf(user))
            database.conversationDAO.insertConversations(listOf(conversation))
            database.messageDAO.insertOrIgnoreMessages(listOf("pending", "deleted", "sent", "hidden").map { id ->
                newRegularMessageEntity(id = id, conversationId = conversation.id, senderUserId = user.id,
                    status = if (id == "sent") MessageEntity.Status.SENT else MessageEntity.Status.PENDING)
            }, withUnreadEvents = false)
            database.messageDAO.deleteMessage("deleted", conversation.id)
            database.messageDAO.markMessageAsDeleted("hidden", conversation.id)
            for (id in listOf("pending", "deleted", "sent", "hidden")) {
                database.messageDAO.updatePendingTextPolicy(conversation.id, id, 60_000L, true)
            }
            val pending = assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("pending", conversation.id))
            assertEquals(60_000L, pending.expireAfterMs)
            assertTrue(pending.expectsReadConfirmation)
            assertEquals(null, database.messageDAO.getMessageById("deleted", conversation.id))
            assertEquals(null, assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("sent", conversation.id)).expireAfterMs)
            assertEquals(null, assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("hidden", conversation.id)).expireAfterMs)
            database.messageDAO.updatePendingTextPolicy(conversation.id, "pending", null, false)
            assertEquals(null, assertIs<MessageEntity.Regular>(database.messageDAO.getMessageById("pending", conversation.id)).expireAfterMs)
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun cancellingHandoffPersistsPendingMessageBeforeCryptoFinishes() = runTest {
        val root = NSTemporaryDirectory() + "wire-pending-handoff-" + NSUUID().UUIDString
        val access = AccountResourceAccess()
        val user = newUserEntity("sender")
        val conversation = newConversationEntity("pending")
        val database = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(root), accountResourceAccess = access),
            user.id, UserDBSecret(TestDatabaseFile.KEY), StandardTestDispatcher(testScheduler),
            enableWAL = true, dbInvalidationControlEnabled = false,
        )
        try {
            database.userDAO.upsertUsers(listOf(user))
            database.conversationDAO.insertConversations(listOf(conversation))
            val finishCrypto = CompletableDeferred<Unit>()
            val crypto = backgroundScope.launch { access.withAccess { finishCrypto.await() } }
            runCurrent()
            var released = false
            val handoff = backgroundScope.launch { access.pause { released = true } }
            runCurrent()
            val persist = async {
                database.messageDAO.insertOrIgnoreMessages(listOf(newRegularMessageEntity(
                    id = "pending-message", conversationId = conversation.id, senderUserId = user.id,
                    status = MessageEntity.Status.PENDING,
                )), withUnreadEvents = false)
            }
            runCurrent()
            assertFalse(persist.isCompleted)
            handoff.cancelAndJoin()
            persist.await()
            assertFalse(released)
            assertFalse(crypto.isCompleted)
            val timeline = database.messageTimelineDAO.open(conversation.id)
            try {
                val page = assertIs<MessageTimelineLoadResultEntity.Success>(timeline.load(MessageTimelineRequestEntity.Latest, 20)).page
                assertEquals("pending-message", page.rows.single().id)
                assertEquals(MessageEntity.Status.PENDING, page.rows.single().status)
            } finally {
                timeline.close()
            }
            finishCrypto.complete(Unit)
            crypto.join()
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun timelineLoadsWhileWritesWaitAndStillObservesInvalidation() = runTest {
        val root = NSTemporaryDirectory() + "wire-timeline-read-" + NSUUID().UUIDString
        val writes = AccountResourceAccess()
        val user = newUserEntity("reader")
        val conversation = newConversationEntity("timeline")
        val database = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(root), accountResourceAccess = writes),
            user.id, UserDBSecret(TestDatabaseFile.KEY), StandardTestDispatcher(testScheduler),
            enableWAL = true, dbInvalidationControlEnabled = false,
        )
        try {
            database.userDAO.upsertUsers(listOf(user))
            database.conversationDAO.insertConversations(listOf(conversation))
            database.messageDAO.insertOrIgnoreMessages(listOf(newRegularMessageEntity(
                id = "local-message", conversationId = conversation.id, senderUserId = user.id,
            )), withUnreadEvents = false)
            val timeline = database.messageTimelineDAO.open(conversation.id)
            try {
                writes.pause {}
                val pendingWrite = async { database.sqlDriver.execute(null, "UPDATE Message SET id = id", 0).await() }
                runCurrent()
                assertFalse(pendingWrite.isCompleted)
                val page = assertIs<MessageTimelineLoadResultEntity.Success>(timeline.load(MessageTimelineRequestEntity.Latest, 20)).page
                assertEquals(listOf("local-message"), page.rows.map { it.id })
                assertFalse(pendingWrite.isCompleted)
                database.sqlDriver.notifyListeners("Message")
                assertTrue(timeline.invalidated.value)
                writes.resume {}
                pendingWrite.await()
            } finally {
                timeline.close()
            }
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test
    fun readSnapshotIsConsistentAcrossExternalWritesAndReleasesAfterCancellation() = runTest {
        val root = NSTemporaryDirectory() + "wire-read-snapshot-" + NSUUID().UUIDString
        val user = UserIDEntity("snapshot", "example.com")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(root)), user, UserDBSecret(TestDatabaseFile.KEY), dispatcher,
            enableWAL = true, dbInvalidationControlEnabled = false,
        )
        val raw = (database.sqlDriver as MutedSqlDriver).delegate
        val reader = requireNotNull((raw as SnapshotSqlDriver).snapshotDriver)
        val transacter = object : SuspendingTransacterImpl(reader) {}
        try {
            database.sqlDriver.execute(null, "CREATE TABLE SnapshotTest (value INTEGER NOT NULL)", 0).await()
            database.sqlDriver.execute(null, "INSERT INTO SnapshotTest VALUES (1)", 0).await()
            val extension = existingUserDatabaseBuilder(root, user, dispatcher, UserDBSecret(TestDatabaseFile.KEY))
            try {
                transacter.transaction {
                    assertEquals(1, reader.readValue())
                    extension.sqlDriver.execute(null, "UPDATE SnapshotTest SET value = 2", 0).await()
                    assertEquals(1, reader.readValue())
                }
                transacter.transaction { assertEquals(2, reader.readValue()) }
                assertFailsWith<Exception> {
                    transacter.transaction { reader.execute(null, "UPDATE SnapshotTest SET value = 3", 0).await() }
                }
                assertFailsWith<CancellationException> {
                    transacter.transaction {
                        assertEquals(2, reader.readValue())
                        throw CancellationException("cancel page")
                    }
                }
                transacter.transaction { assertEquals(2, reader.readValue()) }
            } finally {
                extension.close()
            }
        } finally {
            database.close()
            NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    private suspend fun SqlDriver.readValue(): Long = executeQuery(null, "SELECT value FROM SnapshotTest", {
        check(it.next().value)
        QueryResult.Value(checkNotNull(it.getLong(0)))
    }, 0).await()
}
