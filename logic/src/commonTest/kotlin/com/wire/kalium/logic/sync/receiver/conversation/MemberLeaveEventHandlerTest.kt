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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MemberLeaveEventHandlerTest {

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {

        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val message = memberRemovedMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = eq(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit), messageMatcher = eq(message))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = eq(event.conversationId.toDao()),
                    memberIdList = eq(list)
                )
            }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.Left))

        verify(arrangement.memberDAO).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        verify(arrangement.updateConversationClientsForCurrentCall).coroutine {
            invoke(message.conversationId)
        }.wasInvoked(once)

        verify(arrangement.persistMessageUseCase).coroutine {
            invoke(message)
        }.wasInvoked(once)

    }

    @Test
    fun givenDaoReturnsFailure_whenDeletingMember_thenNothingToDo() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(
                    Either.Left(failure),
                    userIdList = eq(event.removedList.toSet())
                )
                withPersistingMessage(Either.Left(failure))
                withDeleteMembersByQualifiedIDThrows(throws = IllegalArgumentException())
            }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.Left))

        verify(arrangement.memberDAO).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        verify(arrangement.persistMessageUseCase).coroutine {
            invoke(memberRemovedMessage(event))
        }.wasNotInvoked()
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessageAndFetchUsers() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val message = memberRemovedFromTeamMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = eq(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = eq(event.conversationId.toDao()),
                    memberIdList = eq(list)
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = eq(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit), messageMatcher = eq(message))
                withTeamId(Either.Right(TeamId("teamId")))
                withIsAtLeastOneUserATeamMember(Either.Right(true))
            }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.UserDeleted))

        verify(arrangement.userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        verify(arrangement.updateConversationClientsForCurrentCall).coroutine {
            invoke(message.conversationId)
        }.wasInvoked(once)

        verify(arrangement.persistMessageUseCase).coroutine {
            invoke(message)
        }.wasInvoked(once)
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMemberAndSelfIsNotTeamMember_thenDoNothing() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = eq(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = eq(event.conversationId.toDao()),
                    memberIdList = eq(list)
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = eq(event.removedList.toSet()))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
            }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.UserDeleted))

        verify(arrangement.userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::markAsDeleted)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.updateConversationClientsForCurrentCall)
            .suspendFunction(arrangement.updateConversationClientsForCurrentCall::invoke)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(matching {
                it.content is MessageContent.MemberChange.Removed
            })
            .wasInvoked(once)
    }

    @Test
    fun givenNotMembersRemoved_whenResolvingMessageContent_thenNotMessagePersisted() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TeamId("teamId")))
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withMarkAsDeleted(Either.Right(Unit), userId = eq(event.removedList))
                withDeleteMembersByQualifiedID(
                    result = 0,
                    conversationId = eq(event.conversationId.toDao()),
                    memberIdList = eq(list)
                )
                withIsAtLeastOneUserATeamMember(Either.Right(false))
            }

        memberLeaveEventHandler.handle(event)

        verify(arrangement.userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::markAsDeleted)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.updateConversationClientsForCurrentCall)
            .suspendFunction(arrangement.updateConversationClientsForCurrentCall::invoke)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMemberLeaveEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        // given
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = eq(event.removedList.toSet()))
                withPersistingMessage(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = list.size.toLong(),
                    conversationId = eq(event.conversationId.toDao()),
                    memberIdList = eq(list)
                )
            }
        // when
        memberLeaveEventHandler.handle(event)
        // then
        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleConversationMembersChanged)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement : UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        MemberDAOArrangement by MemberDAOArrangementImpl(),
        SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl() {

        @Mock
        val updateConversationClientsForCurrentCall = mock(classOf<UpdateConversationClientsForCurrentCallUseCase>())

        @Mock
        val legalHoldHandler = mock(classOf<LegalHoldHandler>())

        private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

        init {
            given(legalHoldHandler)
                .suspendFunction(legalHoldHandler::handleConversationMembersChanged)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, MemberLeaveEventHandler> = apply(block)
            .let {
                memberLeaveEventHandler = MemberLeaveEventHandlerImpl(
                    memberDAO = memberDAO,
                    userRepository = userRepository,
                    persistMessage = persistMessageUseCase,
                    updateConversationClientsForCurrentCall = lazy { updateConversationClientsForCurrentCall },
                    legalHoldHandler = legalHoldHandler,
                    selfTeamIdProvider = selfTeamIdProvider
                )
                this to memberLeaveEventHandler
            }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
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
            timestampIso = "timestampIso",
            reason = reason
        )

        fun memberRemovedMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.Removed(members = event.removedList),
            conversationId = event.conversationId,
            date = event.timestampIso,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        fun memberRemovedFromTeamMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.RemovedFromTeam(members = event.removedList),
            conversationId = event.conversationId,
            date = event.timestampIso,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    }
}
