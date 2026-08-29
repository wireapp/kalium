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
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
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
        val federationConnections = connectionConversationList.map {
            val otherUserId = requireNotNull(it.otherUser).id
            FederationConnection(
                conversationId = it.conversationId,
                userId = otherUserId,
                otherUserDomain = otherUserId.domain,
            )
        }

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
            dispatcher = testKaliumDispatcher
            withGetFederationConnections(federationConnections)
            withDeleteFederationConnection()
            withGetGroupConversationsWithMembersWithBothDomains(Either.Right(defederatedGroupConversations))
            withGetOneOnOneConversationsWithFederatedMember(Either.Right(defederatedOneOnOneConversations))
            withDefederateUser(Either.Right(Unit))
            withDeleteFederatedMembers()
            withPersistingMessage(Either.Right(Unit))
        }

        // When
        val event = Event.Federation.Delete(
            "id",
            defederatedDomain
        )

        // Then
        useCase.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo).shouldSucceed()

        verifySuspend(VerifyMode.exactly(defederatedConnections.size)) {
            arrangement.connectionRepository.deleteFederationConnection(matches { it.otherUserDomain == defederatedDomain })
        }

        verifySuspend(VerifyMode.not) {
            arrangement.connectionRepository.deleteFederationConnection(any())
        }

        verifySuspend(VerifyMode.exactly(defederatedOneOnOneConversations.size)) {
            arrangement.userRepository.defederateUser(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.deleteFederatedMembers(eq(defederatedUserIdList), eq(selfConversation))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.deleteFederatedMembers(eq(selfUserIdList), eq(defederatedConversation))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.deleteFederatedMembers(
                eq(userIdWithBothDomainsList),
                eq(otherConversation)
            )
        }

        verifySuspend(VerifyMode.exactly(systemMessageCount)) {
            arrangement.persistMessageUseCase.invoke(any())
        }
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
                dispatcher = testKaliumDispatcher
                withGetGroupConversationsWithMembersWithBothDomains(Either.Right(defederatedGroupConversations))
                withDeleteFederatedMembers()
                withPersistingMessage(Either.Right(Unit))
            }

            // When
            val event = Event.Federation.ConnectionRemoved(
                "id",
                listOf(defederatedDomain, defederatedDomainTwo)
            )

            // Then
            useCase.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo).shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.deleteFederatedMembers(
                    eq(defederatedUserIdListTwo),
                    eq(defederatedConversation)
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.deleteFederatedMembers(
                    eq(defederatedUserIdList),
                    eq(defederatedConversationTwo)
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.deleteFederatedMembers(
                    eq(userIdWithBothDomainsList),
                    eq(selfConversation)
                )
            }

            verifySuspend(VerifyMode.exactly(systemMessageCount)) {
                arrangement.persistMessageUseCase.invoke(any())
            }
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

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        ConnectionRepositoryArrangement by ConnectionRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl()
    {

        var dispatcher: KaliumDispatcher = TestKaliumDispatcher

        suspend fun arrange() = run {
            block()
            this@Arrangement to FederationEventReceiverImpl(
                conversationRepository = conversationRepository,
                connectionRepository = connectionRepository,
                userRepository = userRepository,
                persistMessage = persistMessageUseCase,
                selfUserId = selfUserId,
                dispatchers = dispatcher
            )
        }

        suspend fun withDeleteFederatedMembers() {
            everySuspend {
                conversationRepository.deleteFederatedMembers(any(), any())
            } returns Either.Right(Unit)
        }

        fun withGetFederationConnections(connections: List<FederationConnection>) {
            every {
                connectionRepository.getFederationConnections()
            } returns Either.Right(flowOf(connections))
        }

        suspend fun withDeleteFederationConnection() {
            everySuspend {
                connectionRepository.deleteFederationConnection(any())
            } returns Either.Right(Unit)
        }

    }
}
