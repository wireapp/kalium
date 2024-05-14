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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
<<<<<<< HEAD
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
=======
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.matching
>>>>>>> 39f2aa35cd (fix: No SystemMessage on new 1o1 conversation (#2730))
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MemberJoinEventHandlerTest {

    @Test
<<<<<<< HEAD
=======
    fun givenMemberJoinEventWithoutSelfUser_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val newMembers = listOf(Member(TestUser.OTHER_FEDERATED_USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.CONVERSATION.right())
        }

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(event.conversationId))
            .wasNotInvoked()
    }

    @Test
>>>>>>> 39f2aa35cd (fix: No SystemMessage on new 1o1 conversation (#2730))
    fun givenMemberJoinEventWithSelfUser_whenHandlingIt_thenShouldFetchConversation() = runTest {
        val newMembers = listOf(Member(TestUser.SELF.id, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.CONVERSATION.right())
        }

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.fetchConversationIfUnknown(eq(event.conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationRepository.fetchConversation(eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistMembers() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.CONVERSATION.right())
        }

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.persistMembers(eq(newMembers), eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptPersistingMembersAnyway() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationIfUnknownFailingWith(NetworkFailure.NoNetworkConnection(null))
            withFetchConversation(NetworkFailure.NoNetworkConnection(null).left())
            withConversationDetailsByIdReturning(TestConversation.CONVERSATION.right())
        }

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.persistMembers(eq(newMembers), eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.GROUP().right())
        }

        eventHandler.handle(event)

<<<<<<< HEAD
        coVerify {
            arrangement.persistMessage.invoke(
                matches {
=======
        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(
                matching {
>>>>>>> 39f2aa35cd (fix: No SystemMessage on new 1o1 conversation (#2730))
                    it is Message.System && it.content is MessageContent.MemberChange
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldNotPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.CONVERSATION.right())
        }

        eventHandler.handle(event)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberJoinEventWithEmptyId_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers).copy(id = "")

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.GROUP().right())
        }

        eventHandler.handle(event)

<<<<<<< HEAD
        coVerify {
            arrangement.persistMessage.invoke(
                matches {
=======
        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(
                matching {
>>>>>>> 39f2aa35cd (fix: No SystemMessage on new 1o1 conversation (#2730))
                    it is Message.System && it.content is MessageContent.MemberChange && it.id.isNotEmpty()
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        // given
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)
        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .withPersistMembersSucceeding()
            .arrange()
        // when
        eventHandler.handle(event)
        // then
        coVerify {
            arrangement.legalHoldHandler.handleConversationMembersChanged(eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

<<<<<<< HEAD
    private class Arrangement {
        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        private val userRepository = mock(UserRepository::class)

        @Mock
        val legalHoldHandler = mock(LegalHoldHandler::class)

        private val memberJoinEventHandler: MemberJoinEventHandler = MemberJoinEventHandlerImpl(
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            persistMessage = persistMessage,
            legalHoldHandler = legalHoldHandler
        )

        suspend fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(result)
        }

        suspend fun withFetchConversationIfUnknownSucceeding() = apply {
            coEvery {
                conversationRepository.fetchConversationIfUnknown(any())
            }.returns(Either.Right(Unit))
            coEvery {
                conversationRepository.fetchConversation(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            coEvery {
                conversationRepository.fetchConversationIfUnknown(any())
            }.returns(Either.Left(coreFailure))
            coEvery {
                conversationRepository.fetchConversation(any())
            }.returns(Either.Left(coreFailure))
        }

        suspend fun withPersistMembersSucceeding() = apply {
            coEvery {
                conversationRepository.persistMembers(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                userRepository.fetchUsersIfUnknownByIds(any())
            }.returns(result)
        }

        suspend fun arrange() = run {
            coEvery {
                legalHoldHandler.handleConversationMembersChanged(any())
            }.returns(Either.Right(Unit))
            this to memberJoinEventHandler
        }
=======
    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl() {

        fun withFetchConversationSucceeding() = apply {
            withFetchConversationIfUnknownSucceeding()
            withFetchConversation(Unit.right())
        }

        fun arrange() = run {
            block()

            withPersistingMessage(Unit.right())
            withFetchUsersIfUnknownByIdsReturning(Unit.right())
            withPersistMembers(Unit.right())

            this to MemberJoinEventHandlerImpl(conversationRepository, userRepository, persistMessageUseCase, TestUser.SELF.id)
        }
>>>>>>> 39f2aa35cd (fix: No SystemMessage on new 1o1 conversation (#2730))
    }

    private companion object {
        fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()
    }
}
