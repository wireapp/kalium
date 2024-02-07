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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestEvent
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
import io.mockative.eq
import io.mockative.matching
import io.mockative.once
import io.mockative.time
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FederationEventReceiverTest {

    @Test
    fun givenConversationsWithFederatedUsers_whenReceivingFederationDeleteEvent_thenAllConversationsWithThemShouldBeCleared() = runTest {
        // Given
        fun createConnection(conversationId: ConversationId, otherUserId: UserId) = TestConversationDetails.CONNECTION.copy(
            conversationId = conversationId,
            otherUser = TestUser.OTHER.copy(id = otherUserId),
            connection = TestConnection.CONNECTION.copy(
                qualifiedConversationId = conversationId,
                conversationId = conversationId.value,
            )
        )
        val defederatedConnections = List(defederatedUsersCount) {
            createConnection(
                conversationId = ConversationId("def_connection$it", defederatedDomain),
                otherUserId = UserId("connectionDefId$it", defederatedDomain)
            )
        }
        val otherConnections = List(defederatedUsersCount) {
            createConnection(
                conversationId = ConversationId("other_connection$it", otherDomain),
                otherUserId = UserId("connectionOtherId$it", otherDomain)
            )
        }

        val connectionConversationList = defederatedConnections + otherConnections

        val defederatedUserIdList = List(defederatedUsersCount) { UserId(value = "defId$it", domain = defederatedDomain) }
        val selfUserIdList = List(selfUsersCount) { UserId(value = "selfId$it", domain = selfDomain) }

        val userIdWithBothDomainsList = defederatedUserIdList + selfUserIdList
        val defederatedOneOnOneConversations = mapOf(
            selfConversation.copy("1on1") to UserId("someDef", defederatedDomain),
            defederatedConversation.copy("def1on1") to UserId("someDefTwo", defederatedDomain),
        )

        val defederatedGroupConversations = mapOf(
            selfConversation to userIdWithBothDomainsList,
            defederatedConversation to userIdWithBothDomainsList,
            otherConversation to userIdWithBothDomainsList
        )

        // in oneOnOne conversation there will be only one system message about stopping federate
        // in group conversations there will be always 2 system messages: stopping to federate and users removed
        val systemMessageCount = defederatedOneOnOneConversations.size + (defederatedGroupConversations.size * 2)

        val (arrangement, useCase) = arrange {
            withGetConnections(Either.Right(flowOf(connectionConversationList)))
            withDeleteConnection(Either.Right(Unit))
            withGetGroupConversationsWithMembersWithBothDomains(Either.Right(defederatedGroupConversations))
            withGetOneOnOneConversationsWithFederatedMember(Either.Right(defederatedOneOnOneConversations))
            withDefederateUser(Either.Right(Unit))
            withDeleteMembersByQualifiedID(defederatedConnections.size.toLong())
            withPersistingMessage(Either.Right(Unit))
        }

        // When
        val event = Event.Federation.Delete(
            "id",
            defederatedDomain
        )

        // Then
        useCase.onEvent(event, TestEvent.liveDeliveryInfo).shouldSucceed()

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::deleteConnection)
            .with(matching<Connection> { it.qualifiedConversationId.domain == defederatedDomain })
            .wasInvoked(exactly = defederatedConnections.size.time)

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::deleteConnection)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::defederateUser)
            .with(any())
            .wasInvoked(exactly = defederatedOneOnOneConversations.size.time)

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
            .with(eq(defederatedUserIdList.map { it.toDao() }), eq(selfConversation.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
            .with(eq(selfUserIdList.map { it.toDao() }), eq(defederatedConversation.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.memberDAO)
            .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
            .with(eq(userIdWithBothDomainsList.map { it.toDao() }), eq(otherConversation.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasInvoked(exactly = systemMessageCount.time)
    }

    @Test
    fun givenConversationsWithFederatedUsers_whenFederationConnectionRemovedEvent_thenAllConversationsWithThemShouldBeCleared() =
        runTest {
            // Given
            val defederatedUserIdList = List(defederatedUsersCount) { UserId(value = "defId$it", domain = defederatedDomain) }
            val defederatedUserIdListTwo = List(defederatedUsersCountTwo) { UserId(value = "defIdTwo$it", domain = defederatedDomainTwo) }

            val userIdWithBothDomainsList = defederatedUserIdList + defederatedUserIdListTwo

            val defederatedGroupConversations = mapOf(
                defederatedConversation to userIdWithBothDomainsList,
                defederatedConversationTwo to userIdWithBothDomainsList,
                selfConversation to userIdWithBothDomainsList,
            )

            // in group conversations there will be always 2 system messages: stopping to federate and users removed
            val systemMessageCount = defederatedGroupConversations.size * 2

            val (arrangement, useCase) = arrange {
                withGetGroupConversationsWithMembersWithBothDomains(Either.Right(defederatedGroupConversations))
                withDeleteMembersByQualifiedID(defederatedUserIdList.size.toLong())
                withPersistingMessage(Either.Right(Unit))
            }

            // When
            val event = Event.Federation.ConnectionRemoved(
                "id",
                listOf(defederatedDomain, defederatedDomainTwo)
            )

            // Then
            useCase.onEvent(event, TestEvent.liveDeliveryInfo).shouldSucceed()

            verify(arrangement.memberDAO)
                .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
                .with(eq(defederatedUserIdListTwo.map { it.toDao() }), eq(defederatedConversation.toDao()))
                .wasInvoked(exactly = once)

            verify(arrangement.memberDAO)
                .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
                .with(eq(defederatedUserIdList.map { it.toDao() }), eq(defederatedConversationTwo.toDao()))
                .wasInvoked(exactly = once)

            verify(arrangement.memberDAO)
                .suspendFunction(arrangement.memberDAO::deleteMembersByQualifiedID)
                .with(eq(userIdWithBothDomainsList.map { it.toDao() }), eq(selfConversation.toDao()))
                .wasInvoked(exactly = once)

            verify(arrangement.persistMessageUseCase)
                .suspendFunction(arrangement.persistMessageUseCase::invoke)
                .with(any())
                .wasInvoked(exactly = systemMessageCount.time)
        }

    private companion object {
        const val selfDomain = "selfdomain.com"
        const val otherDomain = "otherdomain.com"
        const val defederatedDomain = "defederateddomain.com"
        const val defederatedDomainTwo = "defederateddomaintwo.com"
        const val defederatedUsersCount = 2
        const val defederatedUsersCountTwo = 3
        const val selfUsersCount = 4
        val selfConversation = ConversationId("self_conv", selfDomain)
        val otherConversation = ConversationId("other_conv", otherDomain)
        val defederatedConversation = ConversationId("def_conv", defederatedDomain)
        val defederatedConversationTwo = ConversationId("def_conv_two", defederatedDomainTwo)
        val selfUserId = UserId("selfUserId", selfDomain)
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
            this@Arrangement to FederationEventReceiverImpl(
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
