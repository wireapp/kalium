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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.NoOpPersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DeletedConversationEventHandlerTest {

    @Test
    fun givenADeletedConversationEvent_whenHandlingItAndNotExists_thenShouldSkipTheDeletion() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventHandler) = arrange {
            withGetConversationByIdReturning(null)
            withObserveUser(userId = requireNotNull(event.senderUserId))
            withDeletingConversationSucceeding()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                deleteConversation(any(), eq(TestConversation.ID))
            }
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationAndItsContent() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val (arrangement, eventHandler) = arrange {
            withGetConversationByIdReturning(conversation)
            withObserveUser(flowOf(otherUser), requireNotNull(event.senderUserId))
            withDeletingConversationSucceeding()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                deleteConversation(any(), eq(TestConversation.ID))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                notificationEventsManager.scheduleDeleteConversationNotification(
                    eq(
                        EphemeralConversationNotification(
                            event,
                            conversation,
                            otherUser
                        )
                    )
                )
            }
            verifySuspend(VerifyMode.not) { notificationEventsManager.scheduleMeetingNotification(any()) }
        }
    }

    @Test
    fun givenDeletedConversationWithoutSender_whenHandlingIt_thenSchedulesAnonymousNotificationAndNotifiesHook() = runTest {
        val event = TestEvent.deletedConversation().copy(senderUserId = null)
        val conversation = TestConversation.CONVERSATION
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (arrangement, eventHandler) = arrange(hookNotifier) {
            withGetConversationByIdReturning(conversation)
            withDeletingConversationSucceeding()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                deleteConversation(any(), eq(TestConversation.ID))
            }
            verifySuspend(VerifyMode.not) {
                userRepository.observeUser(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                notificationEventsManager.scheduleDeleteConversationNotification(
                    eq(EphemeralConversationNotification(event, conversation, null))
                )
            }
        }
        assertEquals(
            ConversationDeleteEventData(event.conversationId) to TestUser.USER_ID,
            hookNotifier.conversationDeleteCalls.single()
        )
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItWithError_thenNoSchedulingTheNotification() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val (arrangement, eventHandler) = arrange {
            withGetConversationByIdReturning(conversation)
            withObserveUser(flowOf(otherUser), requireNotNull(event.senderUserId))
            withDeletingConversationFailing()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                notificationEventsManager.scheduleDeleteConversationNotification(any())
            }
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItSuccessfully_thenHookIsNotified() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (arrangement, eventHandler) = arrange(hookNotifier) {
            withGetConversationByIdReturning(conversation)
            withObserveUser(flowOf(otherUser), requireNotNull(event.senderUserId))
            withDeletingConversationSucceeding()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(1, hookNotifier.conversationDeleteCalls.size)
        val (data, selfUserId) = hookNotifier.conversationDeleteCalls.single()
        assertEquals(event.conversationId, data.conversationId)
        assertEquals(TestUser.USER_ID, selfUserId)
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItWithError_thenHookIsStillNotified() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (arrangement, eventHandler) = arrange(hookNotifier) {
            withGetConversationByIdReturning(conversation)
            withObserveUser(flowOf(otherUser), requireNotNull(event.senderUserId))
            withDeletingConversationFailing()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(1, hookNotifier.conversationDeleteCalls.size)
        val (data, selfUserId) = hookNotifier.conversationDeleteCalls.single()
        assertEquals(event.conversationId, data.conversationId)
        assertEquals(TestUser.USER_ID, selfUserId)
    }

    @Test
    fun givenADeletedConversationEvent_whenConversationNotFound_thenHookIsStillNotified() = runTest {
        val event = TestEvent.deletedConversation()
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (arrangement, eventHandler) = arrange(hookNotifier) {
            withGetConversationByIdReturning(null)
            withObserveUser(userId = requireNotNull(event.senderUserId))
            withDeletingConversationSucceeding()
        }

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(1, hookNotifier.conversationDeleteCalls.size)
        val (data, selfUserId) = hookNotifier.conversationDeleteCalls.single()
        assertEquals(event.conversationId, data.conversationId)
        assertEquals(TestUser.USER_ID, selfUserId)
    }

    @Test
    fun givenADeletedMeetingTypeConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationWithMeetingNotification() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = MockConversation.group(event.conversationId).copy(type = Conversation.Type.Group.Meeting)
        val otherUser = TestUser.OTHER
        val meeting = Meeting(
            meetingId = MeetingId("meetingId", conversation.id.domain),
            conversationId = conversation.id,
            creatorId = UserId(conversation.creatorId.orEmpty(), conversation.id.domain),
            title = "Planning",
            startTime = Instant.parse("2026-10-01T09:00:00Z"),
            endTime = Instant.parse("2026-10-01T10:00:00Z"),
            tzid = "UTC",
            recurrence = null,
        )
        val cancelNotification = LocalNotification.Meeting.Cancel(
            eventId = event.id,
            meetingId = meeting.meetingId,
            conversationId = meeting.conversationId,
            meetingTitle = meeting.title,
            author = LocalNotificationMessageAuthor(otherUser.name.orEmpty(), otherUser.previewPicture),
            time = event.dateTime,
        )
        val (arrangement, eventHandler) = arrange {
            withGetConversationByIdReturning(conversation)
            withObserveUser(flowOf(otherUser), requireNotNull(event.senderUserId))
            withDeletingConversationSucceeding()
            withGetMeetingsByConversationId(Either.Right(listOf(meeting)))
        }

        eventHandler.handle(arrangement.transactionContext, event)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                deleteConversation(any(), event.conversationId)
                notificationEventsManager.scheduleMeetingNotification(cancelNotification)
            }
            verifySuspend(VerifyMode.exactly(0)) {
                notificationEventsManager.scheduleDeleteConversationNotification(any())
            }
        }
    }

    private suspend fun arrange(
        hookNotifier: PersistenceEventHookNotifier = NoOpPersistenceEventHookNotifier,
        block: suspend Arrangement.() -> Unit
    ) = Arrangement(hookNotifier, block).arrange()

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = arrange(NoOpPersistenceEventHookNotifier, block)

    private class Arrangement(
        private val hookNotifier: PersistenceEventHookNotifier,
        private val block: suspend Arrangement.() -> Unit
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationRepository = mock<ConversationRepository>()
        val userRepository = mock<UserRepository>()
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        val deleteConversation = mock<DeleteConversationUseCase>()
        val notificationEventsManager = mock<NotificationEventsManager>(mode = MockMode.autoUnit)

        suspend fun withGetConversationByIdReturning(conversation: Conversation?) {
            everySuspend { conversationRepository.getConversationById(any()) } returns if (conversation == null) {
                Either.Left(StorageFailure.DataNotFound)
            } else {
                Either.Right(conversation)
            }
        }

        suspend fun withObserveUser(result: Flow<User?> = flowOf(TestUser.OTHER), userId: UserId = TestUser.OTHER_USER_ID) {
            everySuspend { userRepository.observeUser(eq(userId)) } returns result
        }

        suspend fun withDeletingConversationSucceeding() {
            everySuspend { deleteConversation(any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withDeletingConversationFailing() {
            everySuspend { deleteConversation(any(), any()) } returns Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
        }

        suspend fun withGetMeetingsByConversationId(result: Either<StorageFailure, List<Meeting>>) {
            everySuspend { meetingRepository.getMeetingsByConversationId(any()) } returns (result)
        }

        suspend fun arrange() = run {
            withGetMeetingsByConversationId(Either.Right(emptyList()))
            block()
            this@Arrangement to DeletedConversationEventHandlerImpl(
                conversationRepository = conversationRepository,
                meetingRepository = meetingRepository,
                userRepository = userRepository,
                notificationEventsManager = notificationEventsManager,
                deleteConversation = deleteConversation,
                persistenceEventHookNotifier = hookNotifier,
                selfUserId = TestUser.USER_ID,
            )
        }
    }

    private class RecordingPersistenceEventHookNotifier : PersistenceEventHookNotifier {
        val conversationDeleteCalls = mutableListOf<Pair<ConversationDeleteEventData, UserId>>()

        override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
            conversationDeleteCalls += data to selfUserId
        }

        override suspend fun onConversationLastReadPersisted(
            data: ConversationLastReadEventData,
            selfUserId: UserId
        ) = Unit
    }
}
