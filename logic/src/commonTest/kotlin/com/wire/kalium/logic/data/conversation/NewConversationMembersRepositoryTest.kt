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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.ConversationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NewConversationMembersRepositoryTest {

    @Test
    fun givenASuccessConversationResponse_whenPersistingMembers_ThenShouldSucceedAndCreateASystemMessage() = runTest {
        val conversationId = TestConversation.ENTITY_ID
        val (arrangement, handler) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = handler.persistMembersAdditionToTheConversation(conversationId, CONVERSATION_RESPONSE)

        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::insertMembersWithQualifiedId)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content as? MessageContent.MemberChange.CreationAdded)?.members?.contains(TestUser.OTHER.id) == true
            })
            .wasInvoked(once)
    }

    @Test
    fun givenASuccessConversationResponse_whenMembersItsEmpty_ThenShouldNotCreateTheSystemMessage() = runTest {
        val conversationId = TestConversation.ENTITY_ID
        val (arrangement, handler) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = handler.persistMembersAdditionToTheConversation(
            conversationId,
            CONVERSATION_RESPONSE.copy(members = CONVERSATION_RESPONSE.members.copy(otherMembers = emptyList()))
        )

        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::insertMembersWithQualifiedId)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
    }


    private class Arrangement {
        @Mock
        val conversationDAO = mock(ConversationDAO::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        fun withPersistMessageSuccess() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
        }

        fun arrange() = this to NewConversationMembersRepositoryImpl(
            persistMessage, conversationDAO, TestUser.SELF.id,
        )
    }

    private companion object {
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_member"),
                listOf(ConversationMemberDTO.Other(TestUser.OTHER.id.toApi(), "wire_member"))
            ),
            ConversationGroupRepositoryTest.GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            0UL,
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            ),
            mlsCipherSuiteTag = null,
            receiptMode = ReceiptMode.DISABLED
        )
    }
}
