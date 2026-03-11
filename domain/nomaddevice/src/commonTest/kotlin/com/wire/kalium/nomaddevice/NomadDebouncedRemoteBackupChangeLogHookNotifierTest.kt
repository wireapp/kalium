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

package com.wire.kalium.nomaddevice

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NomadDebouncedRemoteBackupChangeLogHookNotifierTest {

    @Test
    fun givenMultipleTriggersWithinDebounceWindow_whenTriggered_thenSingleFlushIsExecuted() = runTest {
        val invocations = mutableListOf<Long>()
        val controller = createController {
            invocations += testScheduler.currentTime
            success()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(5_000)
        runCurrent()
        controller.onHookTriggered(USER_A)

        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(invocations.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(15_000L), invocations)
    }

    @Test
    fun givenContinuousTriggers_whenTriggered_thenFlushIsForcedByMaxWait() = runTest {
        val invocations = mutableListOf<Long>()
        val controller = createController {
            invocations += testScheduler.currentTime
            success()
        }

        controller.onHookTriggered(USER_A)
        repeat(6) {
            advanceTimeBy(9_000)
            runCurrent()
            controller.onHookTriggered(USER_A)
        }

        advanceTimeBy(5_999)
        runCurrent()
        assertTrue(invocations.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(60_000L), invocations)
    }

    @Test
    fun givenTriggersForDifferentUsers_whenDebouncing_thenEachUserHasIndependentSchedule() = runTest {
        val invocations = mutableListOf<Pair<UserId, Long>>()
        val controller = createController { userId ->
            invocations += userId to testScheduler.currentTime
            success()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(5_000)
        runCurrent()
        controller.onHookTriggered(USER_B)

        advanceTimeBy(5_000)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(USER_A to 10_000L, USER_B to 15_000L), invocations)
    }

    @Test
    fun givenSyncedEntriesRemain_whenFlushing_thenControllerDrainsBacklogUntilZero() = runTest {
        val invocations = mutableListOf<Long>()
        val results = ArrayDeque(
            listOf(
                NomadRemoteBackupChangeLogSyncResult(syncedEntries = 1, postedEvents = 1),
                NomadRemoteBackupChangeLogSyncResult(syncedEntries = 2, postedEvents = 2),
                NomadRemoteBackupChangeLogSyncResult(syncedEntries = 0, postedEvents = 0),
            )
        )
        val controller = createController {
            invocations += testScheduler.currentTime
            Either.Right(results.removeFirst())
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, invocations.size)
    }

    @Test
    fun givenFlushFailures_whenRetrying_thenRetriesUseConfiguredScheduleAndStopAfterMaxAttempts() = runTest {
        val invocations = mutableListOf<Long>()
        val controller = createController {
            invocations += testScheduler.currentTime
            failure()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        advanceTimeBy(200_000)
        runCurrent()

        assertEquals(listOf(10_000L, 20_000L, 40_000L), invocations)
    }

    @Test
    fun givenRetriesExhausted_whenNewHookArrives_thenNewCycleStarts() = runTest {
        val invocations = mutableListOf<Long>()
        val controller = createController {
            invocations += testScheduler.currentTime
            failure()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(listOf(10_000L, 20_000L, 40_000L), invocations)

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf(10_000L, 20_000L, 40_000L, 150_000L), invocations)
    }

    @Test
    fun givenRetryScheduled_whenNewEventsArrive_thenRetryTimerIsNotPostponed() = runTest {
        val invocations = mutableListOf<Long>()
        val results = ArrayDeque(
            listOf(
                failure(),
                success(),
            )
        )
        val controller = createController {
            invocations += testScheduler.currentTime
            results.removeFirst()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        controller.onHookTriggered(USER_A)

        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(10_000L, 20_000L), invocations)
    }

    @Test
    fun givenRetrySuccess_whenNewHookArrives_thenNextCycleUsesDebounceTiming() = runTest {
        val invocations = mutableListOf<Long>()
        val results = ArrayDeque(
            listOf(
                failure(),
                success(),
                success(),
            )
        )
        val controller = createController {
            invocations += testScheduler.currentTime
            results.removeFirst()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        controller.onHookTriggered(USER_A)
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(listOf(10_000L, 20_000L), invocations)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(10_000L, 20_000L, 35_000L), invocations)
    }

    @Test
    fun givenNewTriggerDuringInFlight_whenFlushFinishes_thenPostFlushCycleIsScheduled() = runTest {
        val invocations = mutableListOf<Long>()
        var callCount = 0
        val controller = createController {
            invocations += testScheduler.currentTime
            callCount += 1
            if (callCount == 1) {
                delay(1_000)
            }
            success()
        }

        controller.onHookTriggered(USER_A)
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(50)
        runCurrent()
        controller.onHookTriggered(USER_A)
        advanceTimeBy(950)
        runCurrent()
        assertEquals(1, invocations.size)

        advanceTimeBy(9_050)
        runCurrent()

        assertEquals(2, invocations.size)
    }

    @Test
    fun givenDebouncedWrapper_whenCallbacksAreInvoked_thenDelegateAndSyncTriggerAreBothCalled() = runTest {
        val delegate = RecordingHookNotifier()
        val triggeredUsers = mutableListOf<UserId>()
        val notifier = DebouncedNomadRemoteBackupChangeLogHookNotifier(
            delegate = delegate,
            onHookTriggered = { userId -> triggeredUsers += userId }
        )

        val conversationId = QualifiedID("conversation-id", "wire.test")
        val persisted = PersistedMessageData(
            conversationId = conversationId,
            messageId = "message-id",
            content = MessageContent.Text("hello"),
            date = Instant.fromEpochMilliseconds(0),
            expireAfter = null
        )

        notifier.onMessagePersisted(persisted, USER_A)
        notifier.onMessageDeleted(MessageDeleteEventData(conversationId, "message-id"), USER_A)
        notifier.onReactionPersisted(ReactionEventData(conversationId, "message-id", Instant.fromEpochMilliseconds(0)), USER_A)
        notifier.onReadReceiptPersisted(
            ReadReceiptEventData(conversationId, listOf("m-1", "m-2"), Instant.fromEpochMilliseconds(0)),
            USER_A
        )
        notifier.onConversationDeleted(ConversationDeleteEventData(conversationId), USER_A)
        notifier.onConversationCleared(ConversationClearEventData(conversationId), USER_A)

        assertEquals(1, delegate.persistedCount)
        assertEquals(1, delegate.messageDeletedCount)
        assertEquals(1, delegate.reactionCount)
        assertEquals(1, delegate.readReceiptCount)
        assertEquals(1, delegate.conversationDeletedCount)
        assertEquals(1, delegate.conversationClearedCount)
        assertEquals(6, triggeredUsers.size)
        assertTrue(triggeredUsers.all { it == USER_A })
    }

    private fun TestScope.createController(
        syncUseCase: suspend (UserId) -> Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult>,
    ): NomadRemoteBackupChangeLogDebouncedSyncController =
        NomadRemoteBackupChangeLogDebouncedSyncController(
            scope = backgroundScope,
            config = NomadRemoteBackupDebouncedSyncConfig(),
            syncUseCase = syncUseCase,
            nowMsProvider = { testScheduler.currentTime }
        )

    private fun success(): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> =
        Either.Right(NomadRemoteBackupChangeLogSyncResult(syncedEntries = 0, postedEvents = 0))

    private fun failure(): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> =
        Either.Left(StorageFailure.DataNotFound)

    private class RecordingHookNotifier : PersistenceEventHookNotifier {
        var persistedCount = 0
        var messageDeletedCount = 0
        var reactionCount = 0
        var readReceiptCount = 0
        var conversationDeletedCount = 0
        var conversationClearedCount = 0

        override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
            persistedCount += 1
        }

        override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
            messageDeletedCount += 1
        }

        override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
            reactionCount += 1
        }

        override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
            readReceiptCount += 1
        }

        override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
            conversationDeletedCount += 1
        }

        override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
            conversationClearedCount += 1
        }
    }

    private companion object {
        val USER_A = UserId("user-a", "wire.test")
        val USER_B = UserId("user-b", "wire.test")
    }
}
