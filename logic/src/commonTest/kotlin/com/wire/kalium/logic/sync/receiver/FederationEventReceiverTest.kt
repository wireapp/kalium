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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.member.ConversationsWithMembers
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.matching
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FederationEventReceiverTest {

    @Test
    fun givenMessage_whenDeleting_then2DeleteMessagesAreSentForSelfAndOriginalSender() = runTest {
        val defederatedConversation = ConversationId("1on1", defederatedDomain)

        val connectionConversationList = listOf(
            TestConversationDetails.CONNECTION.copy(
                conversationId = defederatedConversation,
                otherUser = TestUser.OTHER.copy(id = QualifiedID("otherUserId", defederatedDomain))
// TODO Add other self domain and other domain public users
            )
        )

        val (arrangement, useCase) = arrange {
            withGetConnections(Either.Right(flowOf(connectionConversationList)))
            withDeleteConnection(Either.Right(Unit))
            withGetConversationsWithMembersWithBothDomains(
                Either.Right(
                    ConversationsWithMembers(
                        oneOnOne = mapOf(),
                        group = mapOf()
                    )
                )
            )
            withDefederateUser(Either.Right(Unit))
            withDeleteMembersByQualifiedID()
            withPersistingMessage(Either.Right(Unit))
        }

        val event = Event.Federation.Delete(
            "id",
            true,
            defederatedDomain
        )

        useCase.onEvent(event).shouldSucceed()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessageAsDeleted)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
                    it.conversationId == SELF_CONVERSION_ID.first() &&
                            it.content == MessageContent.DeleteForMe(messageId, conversationId)
                }, matching {
                    it == MessageTarget.Conversation()
                })
            .wasInvoked(exactly = once)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
                    it.conversationId == conversationId &&
                            it.content == MessageContent.DeleteMessage(messageId)
                }, matching {
                    it == MessageTarget.Users(listOf(senderUserID))
                })
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val selfDomain = "selfdomain.com"
        val otherDomain = "otherdomain.com"
        val defederatedDomain = "defederateddomain.com"
        val selfConversation = ConversationId("self_conv", selfDomain)
        val otherConversation = ConversationId("other_conv", otherDomain)
        val defederatedConversation = ConversationId("def_conv", defederatedDomain)

        val selfUserId = UserId("selfUserId", "selfUserDomain.sy")
        val SELF_CONVERSION_ID = listOf(ConversationId("selfConversationId", "selfConversationDomain.com"))
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        ConnectionRepositoryArrangement by ConnectionRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        MemberDAOArrangement by MemberDAOArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl() {

        fun arrange() = block().run {
            this to FederationEventReceiverImpl(
                conversationRepository = conversationRepository,
                connectionRepository = connectionRepository,
                userRepository = userRepository,
                memberDAO = memberDAO,
                persistMessage = persistMessageUseCase,
                selfUserId = selfUserId
            )
        }
    }
}
