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
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MemberChangeEventHandlerTest {

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationIfUnknown(any(), eq(event.conversationId), eq(ConversationSyncReason.Other))
        }
    }

    @Test
    fun givenMemberChangeEventMutedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val event = TestEvent.memberChangeMutedStatus()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMutedStatusLocally(Either.Right(Unit))
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMutedStatusLocally(eq(event.conversationId), any(), any())
        }
    }

    @Test
    fun givenMemberChangeEventArchivedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val isNewEventArchiving = true
        val event = TestEvent.memberChangeArchivedStatus(isArchiving = isNewEventArchiving)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateArchivedStatusLocally(Either.Right(Unit))
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateArchivedStatusLocally(
                eq(event.conversationId),
                matches { it == isNewEventArchiving },
                any()
            )
        }
    }

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldUpdateMembers() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }
    }

    @Test
    fun givenMemberChangeEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptUpdateMembersAnyway() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }
    }

    @Test
    fun givenMemberChangeEventAndNotRolePresent_whenHandlingIt_thenShouldIgnoreTheEvent() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChangeIgnored()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.fetchConversationIfUnknown(any(), any(), any())
        }
    }

    @Test
    fun givenSelfUserPromotedToAdmin_whenHandlingMemberChangedRole_thenSystemMessageIsPersisted() = runTest {
        val selfMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = selfMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    val content = message.content
                    content is MessageContent.MemberChange.UserPromotedToAdmin &&
                            content.members == listOf(TestUser.USER_ID)
                }
            )
        }
    }

    @Test
    fun givenOtherUserPromotedToAdmin_whenHandlingMemberChangedRole_thenSystemMessageIsPersisted() = runTest {
        val otherMember = Member(TestUser.OTHER_USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = otherMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withPersistMessageSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    val content = message.content
                    content is MessageContent.MemberChange.UserPromotedToAdmin &&
                            content.members == listOf(TestUser.OTHER_USER_ID)
                }
            )
        }
    }

    @Test
    fun givenUserIsAlreadyAdmin_whenHandlingAdminRoleChange_thenSystemMessageIsNotPersisted() = runTest {
        val adminMember = Member(TestUser.OTHER_USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = adminMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withExistingMembers(listOf(adminMember))
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessage(any())
        }
    }

    @Test
    fun givenSelfUserRoleChangedToMember_whenHandlingMemberChangedRole_thenNoSystemMessageIsPersisted() = runTest {
        val selfMember = Member(TestUser.USER_ID, Member.Role.Member)
        val event = TestEvent.memberChange(member = selfMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessage(any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val conversationRepository = mock<ConversationRepository>()
        private val userRepository = mock<UserRepository>()
        val fetchConversationIfUnknown = mock<FetchConversationIfUnknownUseCase>()
        val persistMessage = mock<PersistMessageUseCase>()

        private val memberChangeEventHandler: MemberChangeEventHandler = MemberChangeEventHandlerImpl(
            conversationRepository = conversationRepository,
            fetchConversationIfUnknown = fetchConversationIfUnknown,
            persistMessage = persistMessage,
            selfUserId = TestUser.USER_ID,
        )

        init {
            everySuspend { conversationRepository.observeConversationMembers(any()) } returns flowOf(emptyList())
        }

        suspend fun withFetchConversationIfUnknownSucceeding() = apply {
            everySuspend {
                fetchConversationIfUnknown(any(), any(), eq(ConversationSyncReason.Other))
            } returns Either.Right(Unit)
        }

        suspend fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            everySuspend {
                fetchConversationIfUnknown(any(), any(), eq(ConversationSyncReason.Other))
            } returns Either.Left(coreFailure)
        }

        suspend fun withUpdateMemberSucceeding() = apply {
            everySuspend {
                conversationRepository.updateMemberFromEvent(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withExistingMembers(members: List<Member>) = apply {
            everySuspend { conversationRepository.observeConversationMembers(any()) } returns flowOf(members)
        }

        suspend fun withUpdateMutedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationRepository.updateMutedStatusLocally(any(), any(), any())
            } returns result
        }

        suspend fun withUpdateArchivedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            } returns result
        }

        suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                userRepository.fetchUsersIfUnknownByIds(any())
            } returns result
        }

        suspend fun withPersistMessageSucceeding() = apply {
            everySuspend { persistMessage(any()) } returns Either.Right(Unit)
        }

        fun arrange() = this to memberChangeEventHandler
    }
}
