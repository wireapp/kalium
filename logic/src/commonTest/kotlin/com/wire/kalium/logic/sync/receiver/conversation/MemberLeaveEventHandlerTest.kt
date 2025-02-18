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
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matchers.EqualsMatcher
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MemberLeaveEventHandlerTest {

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {

        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val message = memberRemovedMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = EqualsMatcher(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit), messageMatcher = EqualsMatcher(message))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(list)
                )
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.memberDAO.deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        coVerify {
            arrangement.updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(message)
        }.wasInvoked(once)

    }

    @Test
    fun givenDaoReturnsFailure_whenDeletingMember_thenNothingToDo() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(
                    Either.Left(failure),
                    userIdList = EqualsMatcher(event.removedList.toSet())
                )
                withPersistingMessage(Either.Left(failure))
                withDeleteMembersByQualifiedIDThrows(throws = IllegalArgumentException())
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.memberDAO.deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(memberRemovedMessage(event))
        }.wasNotInvoked()
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessageAndFetchUsers() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val message = memberRemovedFromTeamMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = EqualsMatcher(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(list)
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = EqualsMatcher(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit), messageMatcher = EqualsMatcher(message))
                withTeamId(Either.Right(TeamId("teamId")))
                withIsAtLeastOneUserATeamMember(Either.Right(true))
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        coVerify {
            arrangement.updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(message)
        }.wasInvoked(once)
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMemberAndSelfIsNotTeamMember_thenDoNothing() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = EqualsMatcher(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(list)
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = EqualsMatcher(event.removedList.toSet()))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        coVerify {
            arrangement.userRepository.markAsDeleted(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberDAO.deleteMembersByQualifiedID(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.updateConversationClientsForCurrentCall.invoke(eq(event.conversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(
                matches {
                    it.content is MessageContent.MemberChange.Removed
                }
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNotMembersRemoved_whenResolvingMessageContent_thenNotMessagePersisted() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TeamId("teamId")))
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withMarkAsDeleted(Either.Right(Unit), userId = EqualsMatcher(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = 0,
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(list)
                )
                withIsAtLeastOneUserATeamMember(Either.Right(false))
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        coVerify {
            arrangement.userRepository.markAsDeleted(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberDAO.deleteMembersByQualifiedID(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.updateConversationClientsForCurrentCall.invoke(eq(event.conversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMemberLeaveEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        // given
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = EqualsMatcher(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(list)
                )
            }
        // when
        memberLeaveEventHandler.handle(event)
        // then
        coVerify {
            arrangement.legalHoldHandler.handleConversationMembersChanged(eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDaoReturnsSuccessAndConversationInDeleteQueue_whenDeletingSelfMember_thenConversationDeleted() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left).copy(removedList = listOf(selfUserId), removedBy = selfUserId)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = EqualsMatcher(event.conversationId.toDao()),
                    memberIdList = EqualsMatcher(event.removedList.map { QualifiedIDEntity(it.value, it.domain) })
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = EqualsMatcher(event.removedList.toSet()))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationsDeleteQueue(listOf(event.conversationId))
                withDeletingConversationSucceeding(EqualsMatcher(event.conversationId))
            }

        memberLeaveEventHandler.handle(event)

        coVerify {
            arrangement.updateConversationClientsForCurrentCall.invoke(eq(event.conversationId))
        }.wasInvoked(exactly = once)
        coVerify { arrangement.conversationRepository.getConversationsDeleteQueue() }.wasInvoked(once)
        coVerify { arrangement.conversationRepository.deleteConversation(event.conversationId) }.wasInvoked(once)
        coVerify { arrangement.conversationRepository.removeConversationFromDeleteQueue(event.conversationId) }.wasInvoked(once)
    }

    private class Arrangement :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        MemberDAOArrangement by MemberDAOArrangementImpl(),
        SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        @Mock
        val updateConversationClientsForCurrentCall = mock(UpdateConversationClientsForCurrentCallUseCase::class)

        @Mock
        val legalHoldHandler = mock(LegalHoldHandler::class)

        private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MemberLeaveEventHandler> = run {
            coEvery {
                legalHoldHandler.handleConversationMembersChanged(any())
            }.returns(Either.Right(Unit))
            withRemoveConversationToDeleteQueue()
            block()
            memberLeaveEventHandler = MemberLeaveEventHandlerImpl(
                memberDAO = memberDAO,
                userRepository = userRepository,
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                updateConversationClientsForCurrentCall = lazy { updateConversationClientsForCurrentCall },
                legalHoldHandler = legalHoldHandler,
                selfTeamIdProvider = selfTeamIdProvider,
                selfUserId = selfUserId
            )
            this to memberLeaveEventHandler
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
        val selfUserId = UserId("self-userId", "domain")
        val userId = UserId("userId", "domain")
        private val qualifiedUserIdEntity = QualifiedIDEntity("userId", "domain")
        private val qualifiedConversationIdEntity = QualifiedIDEntity("conversationId", "domain")

        val conversationId = ConversationId("conversationId", "domain")
        val list = listOf(qualifiedUserIdEntity)

        fun memberLeaveEvent(reason: MemberLeaveReason) = Event.Conversation.MemberLeave(
            id = "id",
            conversationId = conversationId,
            removedBy = userId,
            removedList = listOf(userId),
            dateTime = Instant.UNIX_FIRST_DATE,
            reason = reason
        )

        fun memberRemovedMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.Removed(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        fun memberRemovedFromTeamMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.RemovedFromTeam(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    }
}
