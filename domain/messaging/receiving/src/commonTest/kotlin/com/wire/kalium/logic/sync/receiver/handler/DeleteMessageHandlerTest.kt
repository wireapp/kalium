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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.IncomingMessageDeletionPersistence
import com.wire.kalium.logic.data.message.MessageDeletionSnapshot
import com.wire.kalium.logic.data.notification.DeleteMessageNotificationScheduler
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DeleteMessageHandlerTest {

    @Test
    fun givenDeletedMessageHasRemoteAsset_whenHandled_thenLocalAssetIsDeleted() = runTest {
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf(remoteAssetId), arrangement.assets.calls)
    }

    @Test
    fun givenSelfAuthoredEphemeralOriginalAndUnverifiedDeleteSender_whenHandled_thenHardDeleteRuns() = runTest {
        val arrangement = Arrangement(snapshot(senderUserId = selfUserId, isRegularEphemeral = true))

        arrangement.handler(content, incomingConversationId, unverifiedUserId)

        assertEquals(listOf("lookup", "hard-delete", "hook"), arrangement.operations)
        assertEquals(listOf(storedMessageId to storedConversationId), arrangement.persistence.hardDeletes)
    }

    @Test
    fun givenOriginalSenderAndEphemeralOriginal_whenHandled_thenHardDeleteRuns() = runTest {
        val arrangement = Arrangement(snapshot(isRegularEphemeral = true))

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "hard-delete", "hook"), arrangement.operations)
    }

    @Test
    fun givenSelfDeleteSenderAndEphemeralOriginal_whenHandled_thenHardDeleteRuns() = runTest {
        val arrangement = Arrangement(snapshot(isRegularEphemeral = true))

        arrangement.handler(content, incomingConversationId, selfUserId)

        assertEquals(listOf("lookup", "hard-delete", "hook"), arrangement.operations)
    }

    @Test
    fun givenVerifiedNonEphemeralOriginal_whenHandled_thenNotificationPrecedesStoredTombstone() = runTest {
        val arrangement = Arrangement(snapshot())

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "notification", "tombstone", "hook"), arrangement.operations)
        assertEquals(listOf(storedConversationId to storedMessageId), arrangement.notifications.calls)
        assertEquals(listOf(storedMessageId to storedConversationId), arrangement.persistence.tombstones)
    }

    @Test
    fun givenVerifiedNonRegularOriginal_whenHandled_thenItUsesTheNonEphemeralTombstoneBranch() = runTest {
        val nonRegularSnapshot = snapshot(isRegularEphemeral = false)
        val arrangement = Arrangement(nonRegularSnapshot)

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "notification", "tombstone", "hook"), arrangement.operations)
    }

    @Test
    fun givenUnverifiedSenderAndNoAsset_whenHandled_thenOnlyLookupAndHookRun() = runTest {
        val arrangement = Arrangement(snapshot())

        arrangement.handler(content, incomingConversationId, unverifiedUserId)

        assertEquals(listOf("lookup", "hook"), arrangement.operations)
        assertEquals(emptyList(), arrangement.persistence.hardDeletes)
        assertEquals(emptyList(), arrangement.persistence.tombstones)
        assertEquals(emptyList(), arrangement.notifications.calls)
        assertEquals(emptyList(), arrangement.assets.calls)
    }

    @Test
    fun givenUnverifiedSenderAndRemoteAsset_whenHandled_thenAssetCleanupStillRuns() = runTest {
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))

        arrangement.handler(content, incomingConversationId, unverifiedUserId)

        assertEquals(listOf("lookup", "asset", "hook"), arrangement.operations)
        assertEquals(listOf(remoteAssetId), arrangement.assets.calls)
    }

    @Test
    fun givenLookupLeft_whenHandled_thenAllMessageAndAssetWorkIsSkippedButHookRuns() = runTest {
        val arrangement = Arrangement(snapshot())
        arrangement.persistence.lookupResult = Either.Left(StorageFailure.DataNotFound)

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "hook"), arrangement.operations)
        assertEquals(emptyList(), arrangement.persistence.hardDeletes)
        assertEquals(emptyList(), arrangement.persistence.tombstones)
        assertEquals(emptyList(), arrangement.notifications.calls)
        assertEquals(emptyList(), arrangement.assets.calls)
    }

    @Test
    fun givenStoredIdsDifferFromIncomingIds_whenHandled_thenStoredIdsDriveWorkAndIncomingIdsDriveHook() = runTest {
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf(storedConversationId to storedMessageId), arrangement.notifications.calls)
        assertEquals(listOf(storedMessageId to storedConversationId), arrangement.persistence.tombstones)
        assertEquals(
            listOf(MessageDeleteEventData(incomingConversationId, incomingMessageId) to selfUserId),
            arrangement.hook.calls,
        )
    }

    @Test
    fun givenHardDeleteReturnsLeft_whenHandled_thenAssetCleanupAndHookContinueInOrder() = runTest {
        val arrangement = Arrangement(snapshot(isRegularEphemeral = true, remoteAssetId = remoteAssetId))
        arrangement.persistence.hardDeleteResult = Either.Left(StorageFailure.DataNotFound)

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "hard-delete", "asset", "hook"), arrangement.operations)
    }

    @Test
    fun givenTombstoneReturnsLeft_whenHandled_thenAssetCleanupAndHookContinueInOrder() = runTest {
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.persistence.tombstoneResult = Either.Left(StorageFailure.DataNotFound)

        arrangement.handler(content, incomingConversationId, originalSenderId)

        assertEquals(listOf("lookup", "notification", "tombstone", "asset", "hook"), arrangement.operations)
    }

    @Test
    fun givenAssetCleanupReturnsLeft_whenHandled_thenHookStillRuns() = runTest {
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.assets.result = Either.Left(StorageFailure.DataNotFound)

        arrangement.handler(content, incomingConversationId, unverifiedUserId)

        assertEquals(listOf("lookup", "asset", "hook"), arrangement.operations)
    }

    @Test
    fun givenNotificationThrows_whenHandled_thenMutationAssetAndHookAreSkipped() = runTest {
        val expected = IllegalStateException("notification failed")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.notifications.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "notification"), arrangement.operations)
    }

    @Test
    fun givenNotificationCancellation_whenHandled_thenCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        val expected = CancellationException("notification cancelled")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.notifications.throwable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "notification"), arrangement.operations)
    }

    @Test
    fun givenLookupThrows_whenHandled_thenExceptionEscapesAndHookIsSkipped() = runTest {
        val expected = IllegalStateException("lookup failed")
        val arrangement = Arrangement(snapshot())
        arrangement.persistence.lookupThrowable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup"), arrangement.operations)
    }

    @Test
    fun givenHardDeleteThrows_whenHandled_thenExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        val expected = IllegalStateException("hard delete failed")
        val arrangement = Arrangement(snapshot(isRegularEphemeral = true, remoteAssetId = remoteAssetId))
        arrangement.persistence.hardDeleteThrowable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "hard-delete"), arrangement.operations)
    }

    @Test
    fun givenTombstoneThrows_whenHandled_thenExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        val expected = IllegalStateException("tombstone failed")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.persistence.tombstoneThrowable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "notification", "tombstone"), arrangement.operations)
    }

    @Test
    fun givenAssetCleanupThrows_whenHandled_thenExceptionEscapesAndHookIsSkipped() = runTest {
        val expected = IllegalStateException("asset cleanup failed")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.assets.throwable = expected

        val actual = assertFailsWith<IllegalStateException> {
            arrangement.handler(content, incomingConversationId, unverifiedUserId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "asset"), arrangement.operations)
    }

    @Test
    fun givenLookupCancellation_whenHandled_thenCancellationEscapesAndHookIsSkipped() = runTest {
        val expected = CancellationException("lookup cancelled")
        val arrangement = Arrangement(snapshot())
        arrangement.persistence.lookupThrowable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup"), arrangement.operations)
    }

    @Test
    fun givenHardDeleteCancellation_whenHandled_thenCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        val expected = CancellationException("hard delete cancelled")
        val arrangement = Arrangement(snapshot(isRegularEphemeral = true, remoteAssetId = remoteAssetId))
        arrangement.persistence.hardDeleteThrowable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "hard-delete"), arrangement.operations)
    }

    @Test
    fun givenTombstoneCancellation_whenHandled_thenCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        val expected = CancellationException("tombstone cancelled")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.persistence.tombstoneThrowable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler(content, incomingConversationId, originalSenderId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "notification", "tombstone"), arrangement.operations)
    }

    @Test
    fun givenAssetCleanupCancellation_whenHandled_thenCancellationEscapesAndHookIsSkipped() = runTest {
        val expected = CancellationException("asset cleanup cancelled")
        val arrangement = Arrangement(snapshot(remoteAssetId = remoteAssetId))
        arrangement.assets.throwable = expected

        val actual = assertFailsWith<CancellationException> {
            arrangement.handler(content, incomingConversationId, unverifiedUserId)
        }

        assertSame(expected, actual)
        assertEquals(listOf("lookup", "asset"), arrangement.operations)
    }

    private class Arrangement(snapshot: MessageDeletionSnapshot) {
        val operations = mutableListOf<String>()
        val persistence = RecordingMessageDeletionPersistence(operations, snapshot)
        val assets = RecordingDeleteMessageAssetCleanup(operations)
        val notifications = RecordingDeleteNotificationScheduler(operations)
        val hook = RecordingPersistenceEventHookNotifier(operations)
        val handler = DeleteMessageHandlerImpl(
            messageDeletionPersistence = persistence,
            assetCleanup = assets,
            deleteMessageNotificationScheduler = notifications,
            selfUserId = selfUserId,
            persistenceEventHookNotifier = hook,
        )
    }

    private class RecordingMessageDeletionPersistence(
        private val operations: MutableList<String>,
        snapshot: MessageDeletionSnapshot,
    ) : IncomingMessageDeletionPersistence {
        var lookupResult: Either<StorageFailure, MessageDeletionSnapshot> = Either.Right(snapshot)
        var hardDeleteResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var tombstoneResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var lookupThrowable: Throwable? = null
        var hardDeleteThrowable: Throwable? = null
        var tombstoneThrowable: Throwable? = null
        val hardDeletes = mutableListOf<Pair<String, ConversationId>>()
        val tombstones = mutableListOf<Pair<String, ConversationId>>()

        override suspend fun loadMessageDeletionSnapshot(
            conversationId: ConversationId,
            messageId: String,
        ): Either<StorageFailure, MessageDeletionSnapshot> {
            operations += "lookup"
            lookupThrowable?.let { throw it }
            return lookupResult
        }

        override suspend fun deleteMessage(
            messageUuid: String,
            conversationId: ConversationId,
        ): Either<StorageFailure, Unit> {
            operations += "hard-delete"
            hardDeletes += messageUuid to conversationId
            hardDeleteThrowable?.let { throw it }
            return hardDeleteResult
        }

        override suspend fun markMessageAsDeleted(
            messageUuid: String,
            conversationId: ConversationId,
        ): Either<StorageFailure, Unit> {
            operations += "tombstone"
            tombstones += messageUuid to conversationId
            tombstoneThrowable?.let { throw it }
            return tombstoneResult
        }
    }

    private class RecordingDeleteMessageAssetCleanup(
        private val operations: MutableList<String>,
    ) : DeleteMessageAssetCleanup {
        var result: Either<CoreFailure, Unit> = Either.Right(Unit)
        var throwable: Throwable? = null
        val calls = mutableListOf<String>()

        override suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit> {
            operations += "asset"
            calls += assetId
            throwable?.let { throw it }
            return result
        }
    }

    private class RecordingDeleteNotificationScheduler(
        private val operations: MutableList<String>,
    ) : DeleteMessageNotificationScheduler {
        var throwable: Throwable? = null
        val calls = mutableListOf<Pair<ConversationId, String>>()

        override suspend fun scheduleDeleteMessageNotification(conversationId: ConversationId, messageId: String) {
            operations += "notification"
            calls += conversationId to messageId
            throwable?.let { throw it }
        }
    }

    private class RecordingPersistenceEventHookNotifier(
        private val operations: MutableList<String>,
    ) : PersistenceEventHookNotifier {
        val calls = mutableListOf<Pair<MessageDeleteEventData, UserId>>()

        override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
            operations += "hook"
            calls += data to selfUserId
        }
    }

    private companion object {
        const val incomingMessageId = "incoming-message-id"
        const val storedMessageId = "stored-message-id"
        const val remoteAssetId = "remote-asset-id"
        val incomingConversationId = ConversationId("incoming-conversation", "incoming.example")
        val storedConversationId = ConversationId("stored-conversation", "stored.example")
        val selfUserId = UserId("self", "wire.example")
        val originalSenderId = UserId("original", "wire.example")
        val unverifiedUserId = UserId("unverified", "wire.example")
        val content = MessageContent.DeleteMessage(incomingMessageId)

        fun snapshot(
            senderUserId: UserId = originalSenderId,
            isRegularEphemeral: Boolean = false,
            remoteAssetId: String? = null,
        ) = MessageDeletionSnapshot(
            messageId = storedMessageId,
            conversationId = storedConversationId,
            senderUserId = senderUserId,
            isRegularEphemeral = isRegularEphemeral,
            remoteAssetId = remoteAssetId,
        )
    }
}
