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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.fun1
import io.mockative.fun2
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationEventHandlerTest {

    @Test
    fun givenNewConversationOriginatedFromEvent_whenHandlingIt_thenPersistConversationShouldBeCalled() = runTest {
        val event = testNewConversationEvent()
        val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
        val teamIdValue = "teamId"
        val teamId = TeamId(teamIdValue)
        val creatorQualifiedId = QualifiedID(
            value = "creator",
            domain = ""
        )

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withPersistingConversations(Either.Right(true))
            .withFetchUsersIfUnknownIds(members)
            .withSelfUserTeamId(Either.Right(teamId))
            .withConversationStartedSystemMessage()
            .withConversationUnverifiedWarningSystemMessage()
            .withConversationResolvedMembersSystemMessage()
            .withReadReceiptsSystemMessage()
            .withQualifiedId(creatorQualifiedId)
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistConversation)
            .with(eq(event.conversation), eq(teamIdValue))
            .wasInvoked(exactly = once)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersIfUnknownByIds)
            .with(eq(members))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConversationEvent_whenHandlingIt_thenConversationLastModifiedShouldBeUpdated() = runTest {
        val event = testNewConversationEvent()
        val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
        val teamId = TestTeam.TEAM_ID
        val creatorQualifiedId = QualifiedID(
            value = "creator",
            domain = ""
        )

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withPersistingConversations(Either.Right(true))
            .withFetchUsersIfUnknownIds(members)
            .withSelfUserTeamId(Either.Right(teamId))
            .withConversationStartedSystemMessage()
            .withConversationUnverifiedWarningSystemMessage()
            .withConversationResolvedMembersSystemMessage()
            .withReadReceiptsSystemMessage()
            .withQualifiedId(creatorQualifiedId)
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(eq(event.conversationId), matching { it.wasInTheLastSecond })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingIt_thenPersistTheSystemMessagesForNewConversation() = runTest {
        // given
        val event = testNewConversationEvent(
            conversation = TestConversation.CONVERSATION_RESPONSE.copy(
                creator = "creatorId@creatorDomain",
                receiptMode = ReceiptMode.ENABLED
            ),
        )
        val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
        val teamId = TestTeam.TEAM_ID
        val creatorQualifiedId = QualifiedID(
            value = "creatorId",
            domain = "creatorDomain"
        )

        val (arrangement, eventHandler) = Arrangement()
            .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
            .withPersistingConversations(Either.Right(true))
            .withFetchUsersIfUnknownIds(members)
            .withSelfUserTeamId(Either.Right(teamId))
            .withConversationStartedSystemMessage()
            .withConversationResolvedMembersSystemMessage()
            .withConversationUnverifiedWarningSystemMessage()
            .withReadReceiptsSystemMessage()
            .withQualifiedId(creatorQualifiedId)
            .arrange()

        // when
        eventHandler.handle(event)

        // then
        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(
                arrangement.newGroupConversationSystemMessagesCreator::conversationStarted,
                fun2<UserId, ConversationResponse>()
            )
            .with(any(), eq(event.conversation))
            .wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationResolvedMembersAdded)
            .with(eq(event.conversationId.toDao()), eq(event.conversation.members.otherMembers.map { it.id.toModel() }))
            .wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(
                arrangement.newGroupConversationSystemMessagesCreator::conversationReadReceiptStatus,
                fun1<ConversationResponse>()
            )
            .with(eq(event.conversation))
            .wasInvoked(exactly = once)

        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationStartedUnverifiedWarning)
            .with(eq(event.conversation.id.toModel()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingItAndAlreadyPresent_thenShouldSkipPersistingTheSystemMessagesForNewConversation() =
        runTest {
            // given
            val event = testNewConversationEvent(
                conversation = TestConversation.CONVERSATION_RESPONSE.copy(
                    creator = "creatorId@creatorDomain",
                    receiptMode = ReceiptMode.ENABLED
                ),
            )
            val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
            val teamId = TestTeam.TEAM_ID
            val creatorQualifiedId = QualifiedID(
                value = "creatorId",
                domain = "creatorDomain"
            )

            val (arrangement, eventHandler) = Arrangement()
                .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
                .withPersistingConversations(Either.Right(false))
                .withFetchUsersIfUnknownIds(members)
                .withSelfUserTeamId(Either.Right(teamId))
                .withConversationStartedSystemMessage()
                .withConversationResolvedMembersSystemMessage()
                .withReadReceiptsSystemMessage()
                .withQualifiedId(creatorQualifiedId)
                .arrange()

            // when
            eventHandler.handle(event)

            // then
            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(
                    arrangement.newGroupConversationSystemMessagesCreator::conversationStarted,
                    fun2<UserId, ConversationResponse>()
                )
                .with(any(), eq(event.conversation))
                .wasNotInvoked()

            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationResolvedMembersAdded)
                .with(eq(event.conversationId.toDao()), eq(event.conversation))
                .wasNotInvoked()

            verify(arrangement.newGroupConversationSystemMessagesCreator)
                .suspendFunction(
                    arrangement.newGroupConversationSystemMessagesCreator::conversationReadReceiptStatus,
                    fun1<ConversationResponse>()
                )
                .with(eq(event.conversation))
                .wasNotInvoked()
        }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingIt_thenShouldSkipExecutingOneOnOneResolver() =
        runTest {
            // given
            val event = testNewConversationEvent(
                conversation = TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.GROUP),
            )
            val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
            val teamId = TestTeam.TEAM_ID
            val creatorQualifiedId = QualifiedID("creatorId", "creatorDomain")
            val (arrangement, eventHandler) = Arrangement()
                .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
                .withPersistingConversations(Either.Right(false))
                .withFetchUsersIfUnknownIds(members)
                .withSelfUserTeamId(Either.Right(teamId))
                .withConversationStartedSystemMessage()
                .withConversationResolvedMembersSystemMessage()
                .withReadReceiptsSystemMessage()
                .withQualifiedId(creatorQualifiedId)
                .arrange()

            // when
            eventHandler.handle(event)

            // then
            verify(arrangement.oneOnOneResolver)
                .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUserId)
                .with(any(), eq(true))
                .wasNotInvoked()
            verify(arrangement.oneOnOneResolver)
                .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUser)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.oneOnOneResolver)
                .suspendFunction(arrangement.oneOnOneResolver::scheduleResolveOneOnOneConversationWithUserId)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenNewOneOnOneConversationEvent_whenHandlingIt_thenShouldExecuteOneOnOneResolver() =
        runTest {
            // given
            val event = testNewConversationEvent(
                conversation = TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.ONE_TO_ONE),
            )
            val members = event.conversation.members.otherMembers.map { it.id.toModel() }.toSet()
            val otherUserId = members.first()
            val teamId = TestTeam.TEAM_ID
            val creatorQualifiedId = QualifiedID("creatorId", "creatorDomain")
            val (arrangement, eventHandler) = Arrangement()
                .withUpdateConversationModifiedDateReturning(Either.Right(Unit))
                .withPersistingConversations(Either.Right(false))
                .withFetchUsersIfUnknownIds(members)
                .withSelfUserTeamId(Either.Right(teamId))
                .withConversationStartedSystemMessage()
                .withConversationResolvedMembersSystemMessage()
                .withReadReceiptsSystemMessage()
                .withQualifiedId(creatorQualifiedId)
                .withResolveOneOnOneConversationWithUserId(Either.Right(event.conversationId))
                .arrange()

            // when
            eventHandler.handle(event)

            // then
            verify(arrangement.oneOnOneResolver)
                .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUserId)
                .with(eq(otherUserId), eq(true))
                .wasInvoked(exactly = once)
        }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

        @Mock
        val newGroupConversationSystemMessagesCreator = mock(classOf<NewGroupConversationSystemMessagesCreator>())

        @Mock
        private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

        @Mock
        val oneOnOneResolver = mock(classOf<OneOnOneResolver>())


        private val newConversationEventHandler: NewConversationEventHandler = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamIdProvider,
            newGroupConversationSystemMessagesCreator,
            oneOnOneResolver
        )

        fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withPersistingConversations(result: Either<StorageFailure, Boolean>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::persistConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withConversationStartedSystemMessage() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(
                    newGroupConversationSystemMessagesCreator::conversationStarted,
                    fun2<UserId, ConversationResponse>()
                )
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationResolvedMembersSystemMessage() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(
                    newGroupConversationSystemMessagesCreator::conversationResolvedMembersAdded
                )
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withConversationUnverifiedWarningSystemMessage() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationStartedUnverifiedWarning)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchUsersIfUnknownIds(members: Set<QualifiedID>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(eq(members))
                .thenReturn(Either.Right(Unit))
        }

        fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .then { either }
        }

        fun withReadReceiptsSystemMessage() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationReadReceiptStatus, fun1<ConversationResponse>())
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withQualifiedId(qualifiedId: QualifiedID) = apply {
            given(qualifiedIdMapper)
                .function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(qualifiedId)
        }

        fun withResolveOneOnOneConversationWithUserId(result: Either<CoreFailure, ConversationId>) = apply {
            given(oneOnOneResolver)
                .suspendFunction(oneOnOneResolver::resolveOneOnOneConversationWithUserId)
                .whenInvokedWith(any(), eq(true))
                .thenReturn(result)
        }

        fun arrange() = this to newConversationEventHandler
    }

    companion object {
        private fun testNewConversationEvent(
            conversation: ConversationResponse = TestConversation.CONVERSATION_RESPONSE,
        ) = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            timestampIso = "timestamp",
            conversation = conversation,
            senderUserId = TestUser.SELF.id
        )
    }

}
