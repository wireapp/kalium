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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
    private val conversationDAO = mock(classOf<ConversationDAO>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val persistMessage = mock(classOf<PersistMessageUseCase>())

    @Mock
    private val updateConversationClientsForCurrentCall = mock(classOf<UpdateConversationClientsForCurrentCallUseCase>())

    private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

    @BeforeTest
    fun setup() {
        memberLeaveEventHandler = MemberLeaveEventHandlerImpl(
            conversationDAO = conversationDAO,
            userRepository = userRepository,
            persistMessage = persistMessage,
            updateConversationClientsForCurrentCall = lazy { updateConversationClientsForCurrentCall }
        )
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {
        given(conversationDAO).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.then { Either.Right(Unit) }

        given(userRepository).coroutine {
            fetchUsersIfUnknownByIds(memberLeaveEvent.removedList.toSet())
        }.then { Either.Right(Unit) }

        given(persistMessage).coroutine {
            persistMessage(message)
        }.then { Either.Right(Unit) }

        memberLeaveEventHandler.handle(memberLeaveEvent)

        verify(conversationDAO).coroutine {
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
        given(conversationDAO).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.then { Either.Left(failure) }

        given(userRepository).coroutine {
            fetchUsersIfUnknownByIds(memberLeaveEvent.removedList.toSet())
        }.then { Either.Left(failure) }

        memberLeaveEventHandler.handle(memberLeaveEvent)

        verify(conversationDAO).coroutine {
            deleteMembersByQualifiedID(list, qualifiedConversationIdEntity)
        }.wasInvoked(once)

        verify(persistMessage).coroutine {
            persistMessage.invoke(message)
        }.wasNotInvoked()
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
        val userId = UserId("userId", "domain")
        private val qualifiedUserIdEntity = QualifiedIDEntity("userId", "domain")
        private val qualifiedConversationIdEntity = QualifiedIDEntity("conversationId", "domain")

        val conversationId = ConversationId("conversationId", "domain")
        val list = listOf(qualifiedUserIdEntity)

        val memberLeaveEvent = Event.Conversation.MemberLeave(
            id = "id",
            conversationId = conversationId,
            transient = false,
            removedBy = userId,
            removedList = listOf(userId),
            timestampIso = "timestampIso"
        )
        val message = Message.System(
            id = memberLeaveEvent.id,
            content = MessageContent.MemberChange.Removed(members = memberLeaveEvent.removedList),
            conversationId = memberLeaveEvent.conversationId,
            date = memberLeaveEvent.timestampIso,
            senderUserId = memberLeaveEvent.removedBy,
            status = Message.Status.SENT,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    }
}
