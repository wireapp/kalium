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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeletedConversationEventHandlerTest {

    @Test
    fun givenADeletedConversationEvent_whenHandlingItAndNotExists_thenShouldSkipTheDeletion() = runTest {
        val arrangement = Arrangement().apply { lookupResult = Either.Left(StorageFailure.DataNotFound) }

        arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())

        assertTrue(arrangement.deleteCalls.isEmpty())
        assertTrue(arrangement.userRepository.observeCalls.isEmpty())
        assertTrue(arrangement.notificationManager.calls.isEmpty())
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationAndItsContent() = runTest {
        val arrangement = Arrangement()
        val event = deletedConversationEvent()

        arrangement.handler.handle(arrangement.transactionContext, event)

        val deleteCall = arrangement.deleteCalls.single()
        assertSame(arrangement.transactionContext, deleteCall.transactionContext)
        assertEquals(event.conversationId, deleteCall.conversationId)
        val notification = arrangement.notificationManager.calls.single()
        assertSame(event, notification.conversationEvent)
        assertSame(arrangement.conversation, notification.conversation)
        assertSame(OTHER_USER, notification.user)
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItWithError_thenNoSchedulingTheNotification() = runTest {
        val arrangement = Arrangement().apply { deleteResult = Either.Left(FAILURE) }

        arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())

        assertTrue(arrangement.notificationManager.calls.isEmpty())
        assertTrue(arrangement.userRepository.observeCalls.isEmpty())
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItSuccessfully_thenHookIsNotified() = runTest {
        val arrangement = Arrangement()
        val event = deletedConversationEvent()

        arrangement.handler.handle(arrangement.transactionContext, event)

        val hookCall = arrangement.hook.calls.single()
        assertEquals(ConversationDeleteEventData(event.conversationId), hookCall.data)
        assertEquals(SELF_USER_ID, hookCall.selfUserId)
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItWithError_thenHookIsStillNotified() = runTest {
        val arrangement = Arrangement().apply { deleteResult = Either.Left(FAILURE) }

        arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())

        assertEquals(1, arrangement.hook.calls.size)
        assertEquals(listOf("lookup", "delete", "hook"), arrangement.callOrder)
    }

    @Test
    fun givenADeletedConversationEvent_whenConversationNotFound_thenHookIsStillNotified() = runTest {
        val arrangement = Arrangement().apply { lookupResult = Either.Left(StorageFailure.DataNotFound) }

        arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())

        assertEquals(1, arrangement.hook.calls.size)
        assertEquals(listOf("lookup", "hook"), arrangement.callOrder)
    }

    @Test
    fun givenADeletedMeetingTypeConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationWithoutTheNotification() = runTest {
        val arrangement = Arrangement().apply {
            conversation = conversation(Conversation.Type.Group.Meeting)
            lookupResult = Either.Right(conversation)
        }

        arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())

        assertEquals(1, arrangement.deleteCalls.size)
        assertEquals(listOf(SENDER_USER_ID), arrangement.userRepository.observeCalls)
        assertEquals(1, arrangement.userRepository.flowCollections)
        assertTrue(arrangement.notificationManager.calls.isEmpty())
        assertEquals(listOf("lookup", "delete", "observe", "flow", "hook"), arrangement.callOrder)
    }

    @Test
    fun exactSuccessOrderArgumentsObjectIdentityAndReturnedUnitArePreserved() = runTest {
        val arrangement = Arrangement()
        val event = deletedConversationEvent()

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Unit, result)
        assertEquals(listOf("lookup", "delete", "observe", "flow", "notification", "hook"), arrangement.callOrder)
        assertEquals(listOf(event.conversationId), arrangement.lookupCalls)
        assertEquals(listOf(SENDER_USER_ID), arrangement.userRepository.observeCalls)
        val notification = arrangement.notificationManager.calls.single()
        assertSame(event, notification.conversationEvent)
        assertSame(arrangement.conversation, notification.conversation)
        assertSame(OTHER_USER, notification.user)
        assertEquals(HookCall(ConversationDeleteEventData(event.conversationId), SELF_USER_ID), arrangement.hook.calls.single())
    }

    @Test
    fun lookupAndDeleteReturnedFailuresStillReachHookWhileThrownFailuresDoNot() = runTest {
        val lookupLeft = Arrangement().apply { lookupResult = Either.Left(StorageFailure.DataNotFound) }
        lookupLeft.handler.handle(lookupLeft.transactionContext, deletedConversationEvent())
        assertEquals(listOf("lookup", "hook"), lookupLeft.callOrder)

        val deleteLeft = Arrangement().apply { deleteResult = Either.Left(FAILURE) }
        deleteLeft.handler.handle(deleteLeft.transactionContext, deletedConversationEvent())
        assertEquals(listOf("lookup", "delete", "hook"), deleteLeft.callOrder)

        listOf("lookup", "delete").forEach { stage ->
            val throwable = IllegalStateException(stage)
            val arrangement = Arrangement().apply {
                throwAt = stage
                thrown = throwable
            }
            assertSame(
                throwable,
                catchThrowable { arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent()) },
            )
            assertTrue(arrangement.hook.calls.isEmpty())
        }
    }

    @Test
    fun userFlowFirstOrNullUsesOnlyFirstValueAndPreservesEmptyAndNullSemantics() = runTest {
        val first = Arrangement().apply {
            userRepository.flowFactory = {
                flow {
                    userRepository.flowCollections += 1
                    callOrder += "flow"
                    emit(OTHER_USER)
                    callOrder += "afterFirst"
                    emit(SECOND_USER)
                }
            }
        }
        first.handler.handle(first.transactionContext, deletedConversationEvent())
        assertSame(OTHER_USER, first.notificationManager.calls.single().user)
        assertTrue("afterFirst" !in first.callOrder)

        val empty = Arrangement().apply {
            userRepository.flowFactory = {
                flow {
                    userRepository.flowCollections += 1
                    callOrder += "flow"
                }
            }
        }
        empty.handler.handle(empty.transactionContext, deletedConversationEvent())
        assertNull(empty.notificationManager.calls.single().user)

        val firstNull = Arrangement().apply {
            userRepository.flowFactory = {
                flow {
                    userRepository.flowCollections += 1
                    callOrder += "flow"
                    emit(null)
                    callOrder += "afterNull"
                    emit(OTHER_USER)
                }
            }
        }
        firstNull.handler.handle(firstNull.transactionContext, deletedConversationEvent())
        assertNull(firstNull.notificationManager.calls.single().user)
        assertTrue("afterNull" !in firstNull.callOrder)
    }

    @Test
    fun notificationExceptionPropagatesAndPreventsFinalHook() = runTest {
        val throwable = IllegalStateException("notification")
        val arrangement = Arrangement().apply {
            throwAt = "notification"
            thrown = throwable
        }

        val caught = catchThrowable {
            arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())
        }

        assertSame(throwable, caught)
        assertTrue(arrangement.hook.calls.isEmpty())
    }

    @Test
    fun exceptionsAndCancellationAtEveryStageIncludingFinalHookPropagateByIdentity() = runTest {
        val stages = listOf("lookup", "delete", "observe", "flow", "notification", "hook")

        stages.forEach { stage ->
            listOf(IllegalStateException(stage), CancellationException(stage)).forEach { throwable ->
                val arrangement = Arrangement().apply {
                    throwAt = stage
                    thrown = throwable
                }

                val caught = catchThrowable {
                    arrangement.handler.handle(arrangement.transactionContext, deletedConversationEvent())
                }

                assertSame(throwable, caught, stage)
                assertEquals(stage, arrangement.callOrder.last(), stage)
                if (stage != "hook") assertTrue(arrangement.hook.calls.isEmpty(), stage)
            }
        }
    }

    private class Arrangement {
        val callOrder = mutableListOf<String>()
        val transactionContext = mock<CryptoTransactionContext>()
        var conversation = conversation(Conversation.Type.Group.Regular)
        var lookupResult: Either<StorageFailure, Conversation> = Either.Right(conversation)
        var deleteResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var throwAt: String? = null
        var thrown: Throwable? = null
        val lookupCalls = mutableListOf<ConversationId>()
        val deleteCalls = mutableListOf<DeleteCall>()
        val userRepository = RecordingUserRepository(callOrder) { operation -> throwIfConfigured(operation) }
        val notificationManager = RecordingNotificationEventsManager(callOrder) { operation -> throwIfConfigured(operation) }
        val hook = RecordingHook(callOrder) { operation -> throwIfConfigured(operation) }

        private val repository = object : ConversationEventLookupRepository {
            override suspend fun getConversationById(conversationId: ConversationId): Either<StorageFailure, Conversation> {
                callOrder += "lookup"
                lookupCalls += conversationId
                throwIfConfigured("lookup")
                return lookupResult
            }

            override suspend fun isCellEnabled(conversationId: ConversationId): Either<StorageFailure, Boolean> =
                error("Not used by the deleted-conversation handler")
        }

        val handler = DeletedConversationEventHandlerImpl(
            userRepository = userRepository,
            conversationRepository = repository,
            notificationEventsManager = notificationManager,
            deleteConversation = { transactionContext, conversationId ->
                callOrder += "delete"
                deleteCalls += DeleteCall(transactionContext, conversationId)
                throwIfConfigured("delete")
                deleteResult
            },
            persistenceEventHookNotifier = hook,
            selfUserId = SELF_USER_ID,
        )

        private fun throwIfConfigured(operation: String) {
            if (throwAt == operation) throw checkNotNull(thrown)
        }
    }

    private class RecordingUserRepository(
        private val callOrder: MutableList<String>,
        private val beforeReturn: (String) -> Unit,
    ) : ConversationEventUserRepository {
        val observeCalls = mutableListOf<UserId>()
        var flowCollections = 0
        var flowFactory: () -> Flow<User?> = {
            flow {
                flowCollections += 1
                callOrder += "flow"
                beforeReturn("flow")
                emit(OTHER_USER)
            }
        }

        override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> = Either.Right(Unit)

        override suspend fun observeUser(userId: UserId): Flow<User?> {
            callOrder += "observe"
            observeCalls += userId
            beforeReturn("observe")
            return flowFactory()
        }
    }

    private class RecordingNotificationEventsManager(
        private val callOrder: MutableList<String>,
        private val beforeReturn: (String) -> Unit,
    ) : NotificationEventsManager {
        val calls = mutableListOf<EphemeralConversationNotification>()

        override suspend fun observeEphemeralNotifications(): Flow<com.wire.kalium.logic.data.notification.LocalNotification> = emptyFlow()

        override suspend fun scheduleDeleteConversationNotification(
            ephemeralConversationNotification: EphemeralConversationNotification,
        ) {
            callOrder += "notification"
            calls += ephemeralConversationNotification
            beforeReturn("notification")
        }

        override suspend fun scheduleDeleteMessageNotification(message: Message) = Unit

        override suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.TextEdited) = Unit

        override suspend fun scheduleEditMessageNotification(message: Message, messageContent: MessageContent.MultipartEdited) = Unit

        override suspend fun scheduleConversationSeenNotification(conversationId: ConversationId) = Unit

        override suspend fun scheduleRegularNotificationChecking() = Unit

        override suspend fun observeRegularNotificationsChecking(): Flow<Unit> = emptyFlow()
    }

    private class RecordingHook(
        private val callOrder: MutableList<String>,
        private val beforeReturn: (String) -> Unit,
    ) : PersistenceEventHookNotifier {
        val calls = mutableListOf<HookCall>()

        override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
            callOrder += "hook"
            calls += HookCall(data, selfUserId)
            beforeReturn("hook")
        }

        override suspend fun onConversationLastReadPersisted(data: ConversationLastReadEventData, selfUserId: UserId) = Unit
    }

    private data class DeleteCall(
        val transactionContext: CryptoTransactionContext,
        val conversationId: ConversationId,
    )

    private data class HookCall(val data: ConversationDeleteEventData, val selfUserId: UserId)

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation", "example.com")
        val SENDER_USER_ID = UserId("sender", "example.com")
        val SELF_USER_ID = UserId("self", "example.com")
        val OTHER_USER_ID = UserId("other", "example.com")
        val SECOND_USER_ID = UserId("second", "example.com")
        val EVENT_INSTANT = Instant.parse("2024-01-02T03:04:05Z")
        val FAILURE = CoreFailure.Unknown(IllegalStateException("failure"))
        val OTHER_USER = otherUser(OTHER_USER_ID, "Other")
        val SECOND_USER = otherUser(SECOND_USER_ID, "Second")

        fun deletedConversationEvent() = Event.Conversation.DeletedConversation(
            id = "event-id",
            conversationId = CONVERSATION_ID,
            senderUserId = SENDER_USER_ID,
            dateTime = EVENT_INSTANT,
        )

        fun conversation(type: Conversation.Type) = Conversation(
            id = CONVERSATION_ID,
            name = "conversation",
            type = type,
            teamId = TeamId("team"),
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = EVENT_INSTANT,
            lastReadDate = EVENT_INSTANT,
            access = listOf(Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.TEAM_MEMBER),
            creatorId = SENDER_USER_ID.toString(),
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )

        fun otherUser(id: UserId, name: String) = OtherUser(
            id = id,
            name = name,
            handle = name.lowercase(),
            accentId = 1,
            teamId = TeamId("team"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = null,
            completePicture = null,
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS),
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )

        suspend fun catchThrowable(block: suspend () -> Unit): Throwable? = try {
            block()
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }
}
