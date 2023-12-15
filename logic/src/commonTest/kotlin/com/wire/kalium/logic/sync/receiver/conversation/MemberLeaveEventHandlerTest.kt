/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MemberLeaveEventHandlerTest {

    @Mock
    private val memberDao = mock(classOf<MemberDAO>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val persistMessage = mock(classOf<PersistMessageUseCase>())

    @Mock
    private val updateConversationClientsForCurrentCall = mock(classOf<UpdateConversationClientsForCurrentCallUseCase>())

    @Mock
    private val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

    private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

    @BeforeTest
    fun setup() {
        memberLeaveEventHandler = MemberLeaveEventHandlerImpl(
            memberDAO = memberDao,
            userRepository = userRepository,
            persistMessage = persistMessage,
            updateConversationClientsForCurrentCall = lazy { updateConversationClientsForCurrentCall },
            selfTeamIdProvider
        )
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {

        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val message = message(event)

        given(memberDao).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.then { Either.Right(Unit) }

        given(userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.then { Either.Right(Unit) }

        given(persistMessage).coroutine {
            persistMessage(message)
        }.then { Either.Right(Unit) }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.Left))

        verify(memberDao).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        verify(updateConversationClientsForCurrentCall).coroutine {
            updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }.wasInvoked(once)

        verify(persistMessage).coroutine {
            persistMessage.invoke(message)
        }.wasInvoked(once)

    }

    @Test
    fun givenDaoReturnsFailure_whenDeletingMember_thenNothingToDo() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        given(memberDao).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.then { Either.Left(failure) }

        given(userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.then { Either.Left(failure) }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.Left))

        verify(memberDao).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        verify(persistMessage).coroutine {
            persistMessage.invoke(message(event))
        }.wasNotInvoked()
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessageAndFetchUsers() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val message = message(event)

        given(userRepository).coroutine {
            markUserAsDeletedAndRemoveFromGroupConversations(event.removedList)
        }.then { Either.Right(Unit) }

        given(userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.then { Either.Right(Unit) }

        given(persistMessage).coroutine {
            persistMessage(message)
        }.then { Either.Right(Unit) }

        memberLeaveEventHandler.handle(memberLeaveEvent(reason = MemberLeaveReason.UserDeleted))

        verify(userRepository).coroutine {
            markUserAsDeletedAndRemoveFromGroupConversations(event.removedList)
        }.wasInvoked(once)

        verify(userRepository).coroutine {
            fetchUsersIfUnknownByIds(event.removedList.toSet())
        }.wasInvoked(once)

        verify(updateConversationClientsForCurrentCall).coroutine {
            updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }.wasInvoked(once)

        verify(persistMessage).coroutine {
            persistMessage.invoke(message)
        }.wasInvoked(once)
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
            transient = false,
            live = false,
            removedBy = userId,
            removedList = listOf(userId),
            timestampIso = "timestampIso",
            reason = reason
        )
        fun message(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.Removed(members = event.removedList),
            conversationId = event.conversationId,
            date = event.timestampIso,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    }
}
