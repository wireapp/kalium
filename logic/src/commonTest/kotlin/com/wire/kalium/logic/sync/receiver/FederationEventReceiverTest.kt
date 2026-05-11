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
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.persistence.dao.member.MemberDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
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
        useCase.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo).shouldSucceed()

        verifySuspend(VerifyMode.exactly(defederatedConnections.size)) {
            arrangement.connectionRepository.deleteConnection(matches { it.qualifiedConversationId.domain == defederatedDomain })
        }

        verifySuspend(VerifyMode.not) {
            arrangement.connectionRepository.deleteConnection(any())
        }

        verifySuspend(VerifyMode.exactly(defederatedOneOnOneConversations.size)) {
            arrangement.userRepository.defederateUser(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(eq(defederatedUserIdList.map { it.toDao() }), eq(selfConversation.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(eq(selfUserIdList.map { it.toDao() }), eq(defederatedConversation.toDao()))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(
                eq(userIdWithBothDomainsList.map { it.toDao() }),
                eq(otherConversation.toDao())
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
                withDeleteMembersByQualifiedID(defederatedUserIdList.size.toLong())
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
                arrangement.memberDAO.deleteMembersByQualifiedID(
                    eq(defederatedUserIdListTwo.map { it.toDao() }),
                    eq(defederatedConversation.toDao())
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.memberDAO.deleteMembersByQualifiedID(
                    eq(defederatedUserIdList.map { it.toDao() }),
                    eq(defederatedConversationTwo.toDao())
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.memberDAO.deleteMembersByQualifiedID(
                    eq(userIdWithBothDomainsList.map { it.toDao() }),
                    eq(selfConversation.toDao())
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
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl()
    {
        val conversationRepository = mock<ConversationRepository>()
        val connectionRepository = mock<ConnectionRepository>()
        val userRepository = mock<UserRepository>()
        val memberDAO = mock<MemberDAO>(mode = MockMode.autoUnit)
        val persistMessageUseCase = mock<PersistMessageUseCase>()

        var dispatcher: KaliumDispatcher = TestKaliumDispatcher

        suspend fun arrange() = run {
            block()
            this@Arrangement to FederationEventReceiverImpl(
                conversationRepository = conversationRepository,
                connectionRepository = connectionRepository,
                userRepository = userRepository,
                memberDAO = memberDAO,
                persistMessage = persistMessageUseCase,
                selfUserId = selfUserId,
                dispatchers = dispatcher
            )
        }

        suspend fun withGetConnections(result: Either<com.wire.kalium.common.error.StorageFailure, Flow<List<ConversationDetails>>>) = apply {
            everySuspend { connectionRepository.getConnections() } returns result
        }

        suspend fun withDeleteConnection(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) = apply {
            everySuspend { connectionRepository.deleteConnection(any()) } returns result
        }

        suspend fun withGetGroupConversationsWithMembersWithBothDomains(
            result: Either<com.wire.kalium.common.error.CoreFailure, Map<ConversationId, List<UserId>>>
        ) = apply {
            everySuspend { conversationRepository.getGroupConversationsWithMembersWithBothDomains(any(), any()) } returns result
        }

        suspend fun withGetOneOnOneConversationsWithFederatedMember(
            result: Either<com.wire.kalium.common.error.CoreFailure, Map<ConversationId, UserId>>
        ) = apply {
            everySuspend { conversationRepository.getOneOnOneConversationsWithFederatedMembers(any()) } returns result
        }

        suspend fun withDefederateUser(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend { userRepository.defederateUser(any()) } returns result
        }

        suspend fun withDeleteMembersByQualifiedID(result: Long) = apply {
            everySuspend { memberDAO.deleteMembersByQualifiedID(any(), any()) } returns result
        }

        suspend fun withPersistingMessage(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend { persistMessageUseCase.invoke(any()) } returns result
        }
    }
}
