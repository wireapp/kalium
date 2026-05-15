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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.toConversationType
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversation(any(), eq(event.conversation), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(eq(members))
        }
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        eventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationModifiedDate(eq(event.conversationId), matches { it.wasInTheLastSecond })
        }
    }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingIt_thenPersistTheSystemMessagesForNewConversation() = runTest {
        // given
        val event = testNewConversationEvent(
            conversation = TestConversation.CONVERSATION_RESPONSE.copy(
                id = TestConversation.ID.toApi(),
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
            .withConversationAppsAccessIfEnabled()
            .arrange()

        // when
        eventHandler.handle(arrangement.transactionContext, event)

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStarted(any<UserId>(), eq(event.conversation), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(
                eq(event.conversationId.toDao()),
                eq(event.conversation.members.otherMembers.map { it.id.toModel() }),
                eq(event.dateTime)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(
                eq(event.conversation),
                eq(event.dateTime)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(
                eq(event.conversation.id.toModel()),
                eq(event.dateTime)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled(
                eq(event.id),
                eq(event.conversation.id.toModel()),
                eq(event.conversation.hasAppsAccessEnabled()),
                eq(event.senderUserId),
                eq(event.conversation.toConversationType(teamId))
            )
        }
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
            eventHandler.handle(arrangement.transactionContext, event)

            // then
            verifySuspend(VerifyMode.not) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationStarted(any(), eq(event.conversation), any())
            }

            verifySuspend(VerifyMode.not) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(
                    eq(event.conversationId.toDao()),
                    eq(event.conversation.members.otherMembers.map { it.id.toModel() }),
                    eq(event.dateTime)
                )
            }

            verifySuspend(VerifyMode.not) {
                arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(
                    eq(event.conversation),
                    eq(event.dateTime)
                )
            }
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
            eventHandler.handle(arrangement.transactionContext, event)

            // then
            verifySuspend(VerifyMode.not) {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), any(), eq(true))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.oneOnOneResolver.scheduleResolveOneOnOneConversationWithUserId(any(), any(), any())
            }
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
            eventHandler.handle(arrangement.transactionContext, event)

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), eq(otherUserId), eq(true))
            }
        }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val userRepository = mock<UserRepository>()
        val selfTeamIdProvider = mock<SelfTeamIdProvider>()
        val newGroupConversationSystemMessagesCreator = mock<NewGroupConversationSystemMessagesCreator>()
        private val qualifiedIdMapper = mock<QualifiedIdMapper>()
        val oneOnOneResolver = mock<OneOnOneResolver>()
        val persistConversation = mock<PersistConversationUseCase>()

        init {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled(
                    any<String>(),
                    any<ConversationId>(),
                    any<Boolean>(),
                    any<UserId>(),
                    any<ConversationEntity.Type>()
                )
            } returns Unit.right()
        }

        private val newConversationEventHandler: NewConversationEventHandler = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamIdProvider,
            newGroupConversationSystemMessagesCreator,
            oneOnOneResolver,
            persistConversation
        )

        suspend fun withUpdateConversationModifiedDateReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationRepository.updateConversationModifiedDate(any(), any())
            } returns result
        }

        suspend fun withPersistingConversations(result: Either<StorageFailure, Boolean>) = apply {
            everySuspend {
                persistConversation(any(), any(), any())
            } returns result
        }

        suspend fun withConversationStartedSystemMessage() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStarted(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withConversationResolvedMembersSystemMessage() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationResolvedMembersAdded(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withConversationUnverifiedWarningSystemMessage() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withFetchUsersIfUnknownIds(members: Set<QualifiedID>) = apply {
            everySuspend {
                userRepository.fetchUsersIfUnknownByIds(eq(members))
            } returns Either.Right(Unit)
        }

        suspend fun withSelfUserTeamId(either: Either<CoreFailure, TeamId?>) = apply {
            everySuspend {
                selfTeamIdProvider.invoke()
            } returns either
        }

        suspend fun withReadReceiptsSystemMessage() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<ConversationResponse>(), any<Instant>())
            } returns Either.Right(Unit)
        }

        fun withQualifiedId(qualifiedId: QualifiedID) = apply {
            every {
                qualifiedIdMapper.fromStringToQualifiedID(any())
            } returns qualifiedId
        }

        suspend fun withResolveOneOnOneConversationWithUserId(result: Either<CoreFailure, ConversationId>) = apply {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(any(), any(), eq(true))
            } returns result
        }

        suspend fun withConversationAppsAccessIfEnabled() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled(
                    any<String>(),
                    any<ConversationId>(),
                    any<Boolean>(),
                    any<UserId>(),
                    any<ConversationEntity.Type>()
                )
            } returns Unit.right()
        }

        fun arrange() = this to newConversationEventHandler
    }

    companion object {
        private fun testNewConversationEvent(
            conversation: ConversationResponse = TestConversation.CONVERSATION_RESPONSE,
        ) = Event.Conversation.NewConversation(
            id = "eventId",
            conversationId = TestConversation.ID,
            dateTime = Instant.UNIX_FIRST_DATE,
            conversation = conversation,
            senderUserId = TestUser.SELF.id
        )
    }

}
