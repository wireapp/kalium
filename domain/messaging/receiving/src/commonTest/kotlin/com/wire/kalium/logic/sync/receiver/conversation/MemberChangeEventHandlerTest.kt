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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

class MemberChangeEventHandlerTest {

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val updatedMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(null)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        val call = arrangement.fetchConversationIfUnknown.calls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(event.conversationId, call.conversationId)
    }

    @Test
    fun givenMemberChangeEventMutedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val event = memberChangeMutedStatus()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMutedStatusLocally(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationLifecycleEventRepository.updateMutedStatusLocally(
                eq(event.conversationId),
                any(),
                eq(event.mutedConversationChangedTime)
            )
        }
    }

    @Test
    fun givenMemberChangeEventArchivedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val isNewEventArchiving = true
        val event = memberChangeArchivedStatus(isArchiving = isNewEventArchiving)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateArchivedStatusLocally(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationLifecycleEventRepository.updateArchivedStatusLocally(
                eq(event.conversationId),
                matches { it == isNewEventArchiving },
                eq(event.archivedConversationChangedTime)
            )
        }
    }

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldUpdateMembers() = runTest {
        val updatedMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(null)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationLifecycleEventRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }
    }

    @Test
    fun givenMemberChangeEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptUpdateMembersAnyway() = runTest {
        val updatedMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withConversationMemberRole(null)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationLifecycleEventRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }
    }

    @Test
    fun givenMemberChangeEventAndNotRolePresent_whenHandlingIt_thenShouldIgnoreTheEvent() = runTest {
        val updatedMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChangeIgnored()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.conversationLifecycleEventRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }
        assertEquals(emptyList(), arrangement.fetchConversationIfUnknown.calls)
    }

    @Test
    fun givenSelfUserPromotedToAdmin_whenHandlingMemberChangedRole_thenSystemMessageIsPersisted() = runTest {
        val selfMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChange(member = selfMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(Member.Role.Member)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    val content = message.content
                    content is MessageContent.MemberChange.UserPromotedToAdmin &&
                            content.members == listOf(SELF_USER_ID)
                }
            )
        }
    }

    @Test
    fun givenOtherUserPromotedToAdmin_whenHandlingMemberChangedRole_thenSystemMessageIsPersisted() = runTest {
        val otherMember = Member(OTHER_USER_ID, Member.Role.Admin)
        val event = memberChange(member = otherMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(null)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    val content = message.content
                    content is MessageContent.MemberChange.UserPromotedToAdmin &&
                            content.members == listOf(OTHER_USER_ID)
                }
            )
        }
    }

    @Test
    fun givenUserAlreadyAdmin_whenHandlingMemberChangedRoleToAdmin_thenSystemMessageIsNotPersisted() = runTest {
        val selfMember = Member(SELF_USER_ID, Member.Role.Admin)
        val event = memberChange(member = selfMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(Member.Role.Admin)
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessage(any())
        }
    }

    @Test
    fun givenSelfUserRoleChangedToMember_whenHandlingMemberChangedRole_thenNoSystemMessageIsPersisted() = runTest {
        val selfMember = Member(SELF_USER_ID, Member.Role.Member)
        val event = memberChange(member = selfMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withConversationMemberRole(Member.Role.Admin)
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessage(any())
        }
    }

    @Test
    fun givenPromotionSucceeds_whenHandling_thenRoleReadFetchUpdateAndPersistenceRunInExactOrderWithExactMessage() = runTest {
        val promotedMember = Member(OTHER_USER_ID, Member.Role.Admin)
        val event = memberChange(member = promotedMember)
        val (arrangement, eventHandler) = Arrangement()
            .withConversationMemberRole(Member.Role.Member)
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(listOf("role", "fetch", "update", "persist"), arrangement.callOrder)
        val message = arrangement.persistedMessages.single() as Message.System
        assertEquals(event.id, message.id)
        assertEquals(MessageContent.MemberChange.UserPromotedToAdmin(listOf(promotedMember.id)), message.content)
        assertEquals(event.conversationId, message.conversationId)
        assertEquals(event.dateTime, message.date)
        assertEquals(SELF_USER_ID, message.senderUserId)
        assertEquals(Message.Status.Sent, message.status)
        assertEquals(Message.Visibility.VISIBLE, message.visibility)
        assertEquals(null, message.expirationData)
    }

    @Test
    fun givenFetchAndUpdateReturnLeft_whenHandling_thenFetchIsIgnoredAndUpdateControlsFollowingWork() = runTest {
        val event = memberChange(Member(OTHER_USER_ID, Member.Role.Admin))
        val (arrangement, eventHandler) = Arrangement()
            .withConversationMemberRole(Member.Role.Member)
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(listOf("role", "fetch", "update"), arrangement.callOrder)
        verifySuspend(VerifyMode.not) { arrangement.persistMessage(any()) }
    }

    @Test
    fun givenPromotionPersistenceReturnsLeft_whenHandling_thenReturnedFailureIsIgnored() = runTest {
        val event = memberChange(Member(OTHER_USER_ID, Member.Role.Admin))
        val (arrangement, eventHandler) = Arrangement()
            .withConversationMemberRole(Member.Role.Member)
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        assertEquals(listOf("role", "fetch", "update", "persist"), arrangement.callOrder)
        assertEquals(1, arrangement.persistedMessages.size)
    }

    @Test
    fun givenAnyRoleChangeDependencyThrows_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, IllegalStateException("$stage failed"))
        }
    }

    @Test
    fun givenAnyRoleChangeDependencyCancels_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, CancellationException("$stage cancelled"))
        }
    }

    private suspend fun assertEscapingFailure(stage: FailureStage, expected: Throwable) {
        val (arrangement, eventHandler) = Arrangement()
            .withConversationMemberRole(Member.Role.Member, expected.takeIf { stage == FailureStage.ROLE_READ })
            .withFetchConversationIfUnknownSucceeding(expected.takeIf { stage == FailureStage.FETCH })
            .withUpdateMemberSucceeding(expected.takeIf { stage == FailureStage.UPDATE })
            .withPersistMessageSucceeding(expected.takeIf { stage == FailureStage.PERSIST })
            .arrange()

        val actual = try {
            eventHandler.handle(arrangement.transactionContext, memberChange(Member(OTHER_USER_ID, Member.Role.Admin)))
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual)
        assertEquals(FailureStage.entries.take(stage.ordinal + 1).map { it.callName }, arrangement.callOrder)
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>()
        val conversationLifecycleEventRepository = mock<ConversationLifecycleEventRepository>()
        val persistMessage = mock<PersistMessageUseCase>()
        val callOrder = mutableListOf<String>()
        val persistedMessages = mutableListOf<Message.Standalone>()
        val fetchConversationIfUnknown = FetchConversationIfUnknownRecorder(callOrder)

        private val memberChangeEventHandler: MemberChangeEventHandler = MemberChangeEventHandlerImpl(
            conversationLifecycleEventRepository = conversationLifecycleEventRepository,
            fetchConversationIfUnknown = fetchConversationIfUnknown::invoke,
            persistMessage = persistMessage,
            selfUserId = SELF_USER_ID,
        )

        fun withFetchConversationIfUnknownSucceeding(throwable: Throwable? = null) = apply {
            fetchConversationIfUnknown.result = Either.Right(Unit)
            fetchConversationIfUnknown.throwable = throwable
        }

        fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            fetchConversationIfUnknown.result = Either.Left(coreFailure)
        }

        fun withUpdateMemberSucceeding(throwable: Throwable? = null) =
            withUpdateMemberReturning(Either.Right(Unit), throwable)

        fun withUpdateMemberReturning(result: Either<CoreFailure, Unit>, throwable: Throwable? = null) = apply {
            everySuspend {
                conversationLifecycleEventRepository.updateMemberFromEvent(any(), any())
            } calls {
                callOrder += "update"
                throwable?.let { throw it }
                result
            }
        }

        fun withConversationMemberRole(role: Member.Role?, throwable: Throwable? = null) = apply {
            everySuspend {
                conversationLifecycleEventRepository.getConversationMemberRole(any(), any())
            } calls {
                callOrder += "role"
                throwable?.let { throw it }
                Either.Right(role)
            }
        }

        fun withUpdateMutedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationLifecycleEventRepository.updateMutedStatusLocally(any(), any(), any())
            } returns result
        }

        fun withUpdateArchivedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationLifecycleEventRepository.updateArchivedStatusLocally(any(), any(), any())
            } returns result
        }

        fun withPersistMessageSucceeding(throwable: Throwable? = null) =
            withPersistMessageReturning(Either.Right(Unit), throwable)

        fun withPersistMessageReturning(result: Either<CoreFailure, Unit>, throwable: Throwable? = null) = apply {
            everySuspend { persistMessage(any()) } calls {
                callOrder += "persist"
                @Suppress("UNCHECKED_CAST")
                persistedMessages += it.args[0] as Message.Standalone
                throwable?.let { throw it }
                result
            }
        }

        fun arrange() = this to memberChangeEventHandler
    }

    private class FetchConversationIfUnknownRecorder(
        private val callOrder: MutableList<String>,
    ) {
        var result: Either<CoreFailure, Unit> = Either.Right(Unit)
        var throwable: Throwable? = null
        val calls = mutableListOf<FetchCall>()

        suspend fun invoke(
            transactionContext: CryptoTransactionContext,
            conversationId: ConversationId,
        ): Either<CoreFailure, Unit> {
            callOrder += "fetch"
            calls += FetchCall(transactionContext, conversationId)
            throwable?.let { throw it }
            return result
        }
    }

    private data class FetchCall(
        val transactionContext: CryptoTransactionContext,
        val conversationId: ConversationId,
    )

    private enum class FailureStage(val callName: String) {
        ROLE_READ("role"),
        FETCH("fetch"),
        UPDATE("update"),
        PERSIST("persist"),
    }

    private companion object {
        val SELF_USER_ID = UserId("41d2b365-f4a9-4ba1-bddf-5afb8aca6786", "domain")
        val OTHER_USER_ID = SELF_USER_ID.copy(value = "otherValue")
        val CONVERSATION_ID = ConversationId("valueConvo", "domainConvo")
        val MEMBER_CHANGE_TIME = Instant.parse("2022-03-30T15:36:00.000Z")
        val ARCHIVED_CHANGE_TIME = Instant.parse("2022-03-31T16:36:00.000Z")

        fun memberChange(member: Member, eventId: String = "eventId") =
            Event.Conversation.MemberChanged.MemberChangedRole(
                id = eventId,
                conversationId = CONVERSATION_ID,
                dateTime = MEMBER_CHANGE_TIME,
                member = member,
            )

        fun memberChangeMutedStatus(eventId: String = "eventId") =
            Event.Conversation.MemberChanged.MemberMutedStatusChanged(
                id = eventId,
                conversationId = CONVERSATION_ID,
                mutedConversationStatus = MutedConversationStatus.AllAllowed,
                mutedConversationChangedTime = MEMBER_CHANGE_TIME,
            )

        fun memberChangeArchivedStatus(eventId: String = "eventId", isArchiving: Boolean = true) =
            Event.Conversation.MemberChanged.MemberArchivedStatusChanged(
                id = eventId,
                conversationId = CONVERSATION_ID,
                archivedConversationChangedTime = ARCHIVED_CHANGE_TIME,
                isArchiving = isArchiving,
            )

        fun memberChangeIgnored(eventId: String = "eventId") =
            Event.Conversation.MemberChanged.IgnoredMemberChanged(
                id = eventId,
                conversationId = CONVERSATION_ID,
            )
    }
}
