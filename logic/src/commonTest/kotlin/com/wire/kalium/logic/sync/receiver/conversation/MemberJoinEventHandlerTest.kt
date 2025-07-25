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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.NewGroupConversationSystemMessageCreatorArrangement
import com.wire.kalium.logic.util.arrangement.NewGroupConversationSystemMessageCreatorArrangementImpl
import com.wire.kalium.logic.util.arrangement.eventHandler.LegalHoldHandlerArrangement
import com.wire.kalium.logic.util.arrangement.eventHandler.LegalHoldHandlerArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationIfUnknownUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationIfUnknownUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MemberJoinEventHandlerTest {

    @Test
    fun givenMemberJoinEventWithSelfUser_whenHandlingIt_thenShouldFetchConversation() = runTest {
        val newMembers = listOf(Member(TestUser.SELF.id, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TEST_GROUP_CONVERSATION.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.fetchConversation(any(), eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistMembers() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TEST_GROUP_CONVERSATION.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

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
            withFetchConversationFailingWith(NetworkFailure.NoNetworkConnection(null))
            withConversationDetailsByIdReturning(TEST_GROUP_CONVERSATION.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.conversationRepository.persistMembers(eq(newMembers), eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldPersistMemberChangeSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val conversation = TEST_GROUP_CONVERSATION
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    it is Message.System && it.content is MessageContent.MemberChange
                }
            )
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldNotPersistMemberChangeSystemMessage() = runTest {
        val userId = TestUser.USER_ID
        val conversation = TEST_ONE_ON_ONE_CONVERSATION
        val newMembers = listOf(Member(userId, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withUpdateActiveOneOnOneConversationIfNotSet(Unit.right())
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.persistMessageUseCase(any())
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversationIfNotSet(
                userId = any(),
                conversationId = any()
            )
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenSelfMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldPersistUnverifiedWarningSystemMessage() = runTest {
        val newMembers = listOf(Member(TEST_SELF_USER_ID, Member.Role.Admin))
        val conversation = TEST_GROUP_CONVERSATION
        val event = TestEvent.memberJoin(members = newMembers).copy(conversationId = conversation.id)
        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOtherMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldNotPersistUnverifiedWarningSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.OTHER_USER_ID, Member.Role.Admin))
        val conversation = TEST_GROUP_CONVERSATION
        val event = TestEvent.memberJoin(members = newMembers).copy(conversationId = conversation.id)
        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSelfMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldPersistUnverifiedWarningSystemMessage() = runTest {
        val conversation = TEST_ONE_ON_ONE_CONVERSATION
        val newMembers = listOf(Member(TEST_SELF_USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers).copy(conversationId = conversation.id)
        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withUpdateActiveOneOnOneConversationIfNotSet(Unit.right())
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOtherMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldNotPersistUnverifiedWarningSystemMessage() = runTest {
        val conversation = TEST_ONE_ON_ONE_CONVERSATION
        val newMembers = listOf(Member(TestUser.OTHER_USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers).copy(conversationId = conversation.id)
        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withUpdateActiveOneOnOneConversationIfNotSet(Unit.right())
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(conversation.id), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMemberJoinEventIn1o1Conversation_whenHandlingIt_1o1ConversationForTheUserShouldBeSetIffItWasNotBefore() = runTest {
        val userId = TestUser.USER_ID
        val conversation = TEST_ONE_ON_ONE_CONVERSATION
        val newMembers = listOf(Member(userId, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withUpdateActiveOneOnOneConversationIfNotSet(Unit.right())
            withConversationDetailsByIdReturning(conversation.right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversationIfNotSet(
                userId = any(),
                conversationId = any()
            )
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenMemberJoinEventWithEmptyId_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers).copy(id = "")

        val (arrangement, eventHandler) = arrange {
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.GROUP().right())
        }

        eventHandler.handle(arrangement.transactionContext, event)

        coVerify {
            arrangement.persistMessageUseCase.invoke(
                matches {
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
        val (arrangement, eventHandler) = arrange {
            withPersistingMessage(Either.Right(Unit))
            withFetchConversationSucceeding()
            withConversationDetailsByIdReturning(TestConversation.GROUP().right())
            withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            withPersistMembers(Unit.right())
        }
        // when
        eventHandler.handle(arrangement.transactionContext, event)
        // then
        coVerify {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    it is Message.System && it.content is MessageContent.MemberChange && it.id.isNotEmpty()
                }
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        LegalHoldHandlerArrangement by LegalHoldHandlerArrangementImpl(),
        FetchConversationIfUnknownUseCaseArrangement by FetchConversationIfUnknownUseCaseArrangementImpl(),
        FetchConversationUseCaseArrangement by FetchConversationUseCaseArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl(),
        NewGroupConversationSystemMessageCreatorArrangement by NewGroupConversationSystemMessageCreatorArrangementImpl() {

        suspend fun arrange() = run {
            block()

            withPersistingMessage(Unit.right())
            withFetchUsersIfUnknownByIdsReturning(Unit.right())
            withPersistMembers(Unit.right())
            withHandleConversationMembersChanged(Unit.right())
            withPersistUnverifiedWarningMessageSuccess()

            this to MemberJoinEventHandlerImpl(
                conversationRepository = conversationRepository,
                userRepository = userRepository,
                persistMessage = persistMessageUseCase,
                legalHoldHandler = legalHoldHandler,
                newGroupConversationSystemMessagesCreator = newGroupConversationSystemMessagesCreator,
                selfUserId = TEST_SELF_USER_ID,
                fetchConversation
            )
        }
    }

    private companion object {
        suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

        val TEST_GROUP_CONVERSATION = TestConversation.GROUP()
        val TEST_ONE_ON_ONE_CONVERSATION = TestConversation.ONE_ON_ONE()
        val TEST_SELF_USER_ID = TestUser.SELF.id
    }
}
