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
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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

        coVerify {
            arrangement.conversationRepository.persistConversation(eq(event.conversation), eq(teamIdValue), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userRepository.fetchUsersIfUnknownByIds(eq(members))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationRepository.updateConversationModifiedDate(eq(event.conversationId), matches { it.wasInTheLastSecond })
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStarted(any<UserId>(), eq(event.conversation), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(
                eq(event.conversationId.toDao()),
                eq(event.conversation.members.otherMembers.map { it.id.toModel() }),
                eq(event.timestampIso)
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(
                eq(event.conversation),
                eq(event.timestampIso)
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(eq(event.conversation.id.toModel()))
        }.wasInvoked(exactly = once)
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
            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStarted(any(), eq(event.conversation), any())
            }.wasNotInvoked()

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(
                    eq(event.conversationId.toDao()),
                    eq(event.conversation.members.otherMembers.map { it.id.toModel() }),
                    eq(event.timestampIso)
                )
            }.wasNotInvoked()

            coVerify {
                arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(
                    eq(event.conversation),
                    eq(event.timestampIso)
                )
            }.wasNotInvoked()
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
            coVerify {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), eq(true))
            }.wasNotInvoked()
            coVerify {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any())
            }.wasNotInvoked()
            coVerify {
                arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(eq(otherUserId), eq(true))
            }.wasInvoked(exactly = once)
        }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val newGroupConversationSystemMessagesCreator = mock(NewGroupConversationSystemMessagesCreator::class)

        @Mock
        private val qualifiedIdMapper = mock(QualifiedIdMapper::class)

        @Mock
        val oneOnOneResolver = mock(OneOnOneResolver::class)


        private val newConversationEventHandler: NewConversationEventHandler = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamIdProvider,
            newGroupConversationSystemMessagesCreator,
            oneOnOneResolver
        )

        suspend fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateConversationModifiedDate(any(), any())
            }.returns(result)
        }

        suspend fun withPersistingConversations(result: Either<StorageFailure, Boolean>) = apply {
            coEvery {
                conversationRepository.persistConversation(any(), any(), any())
            }.returns(result)
        }

        suspend fun withConversationStartedSystemMessage() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStarted(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withConversationResolvedMembersSystemMessage() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withConversationUnverifiedWarningSystemMessage() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchUsersIfUnknownIds(members: Set<QualifiedID>) = apply {
            coEvery {
                userRepository.fetchUsersIfUnknownByIds(eq(members))
            }.returns(Either.Right(Unit))
        }

        suspend fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(either)
        }

        suspend fun withReadReceiptsSystemMessage() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<ConversationResponse>(), any<String>())
            }.returns(Either.Right(Unit))
        }

        fun withQualifiedId(qualifiedId: QualifiedID) = apply {
            every {
                qualifiedIdMapper.fromStringToQualifiedID(any())
            }.returns(qualifiedId)
        }

        suspend fun withResolveOneOnOneConversationWithUserId(result: Either<CoreFailure, ConversationId>) = apply {
            coEvery {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), eq(true))
            }.returns(result)
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
