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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.MLSOneOnOneConversationResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSOneOnOneConversationResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OneOnOneMigratorTest {

    @Test
    fun givenOneOnOneIsAlreadyProteus_whenMigratingToProteus_thenShouldNotDoAnythingElseAndSucceed() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = TestConversation.ID
        )

        val (arrangement, oneOneMigrator) = arrange {
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(TestConversation.ID)))
        }

        oneOneMigrator.migrateToProteus(user)
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenUnassignedOneOnOne_whenMigratingToProteus_thenShouldAssignOneOnOneConversation() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )

        val (arrangement, oneOneMigrator) = arrange {
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(TestConversation.ID)))
            withUpdateOneOnOneConversationReturning(Either.Right(Unit))
        }

        oneOneMigrator.migrateToProteus(user)
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(user.id), eq(TestConversation.ID))
            .wasInvoked()
    }

    @Test
    fun givenNoExistingTeamOneOnOne_whenMigratingToProteus_thenShouldCreateGroupConversation() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )

        val (arrangement, oneOneMigrator) = arrange {
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(emptyList()))
            withCreateGroupConversationReturning(Either.Right(TestConversation.ONE_ON_ONE()))
            withUpdateOneOnOneConversationReturning(Either.Right(Unit))
        }

        oneOneMigrator.migrateToProteus(user)
            .shouldSucceed()

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(null), eq(listOf(TestUser.OTHER.id)), eq(ConversationOptions()))
            .wasInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(TestUser.OTHER.id), eq(TestConversation.ONE_ON_ONE().id))
            .wasInvoked()
    }

    @Test
    fun givenOneOnOneIsAlreadyMLS_whenMigratingToMLS_thenShouldNotDoAnythingElseAndSucceed() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = TestConversation.ID
        )

        val (arrangement, oneOneMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
        }

        oneOneMigrator.migrateToMLS(user)
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenResolvingMLSConversationFails_whenMigratingToMLS_thenShouldPropagateFailure() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )
        val failure = CoreFailure.MissingClientRegistration

        val (arrangement, oneOnOneMigrator) = arrange {
            withResolveConversationReturning(Either.Left(failure))
        }

        oneOnOneMigrator.migrateToMLS(user)
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenMigratingMessagesFails_whenMigratingToMLS_thenShouldPropagateFailureAndNotUpdateConversation() = runTest {
        val failure = StorageFailure.DataNotFound
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )
        val (arrangement, oneOnOneMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(TestConversation.ID)))
            withMoveMessagesToAnotherConversation(Either.Left(failure))
        }

        oneOnOneMigrator.migrateToMLS(user)
            .shouldFail {
                assertEquals(failure, it)
            }

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenUpdatingOneOnOneConversationFails_whenMigratingToMLS_thenShouldPropagateFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )
        val (_, oneOnOneMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(TestConversation.ID)))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateOneOnOneConversationReturning(Either.Left(failure))
        }

        oneOnOneMigrator.migrateToMLS(user)
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenResolvedMLSConversation_whenMigratingToMLS_thenShouldMoveMessagesCorrectly() = runTest {
        val originalConversationId = ConversationId("someRandomConversationId", "testDomain")
        val resolvedConversationId = ConversationId("resolvedMLSConversationId", "anotherDomain")
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )
        val (arrangement, oneOnOneMigrator) = arrange {
            withResolveConversationReturning(Either.Right(resolvedConversationId))
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(originalConversationId)))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateOneOnOneConversationReturning(Either.Right(Unit))
        }

        oneOnOneMigrator.migrateToMLS(user)
            .shouldSucceed()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(eq(originalConversationId), eq(resolvedConversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenResolvedMLSConversation_whenMigratingToMLS_thenCallRepositoryWithCorrectArguments() = runTest {
        val originalConversationId = ConversationId("someRandomConversationId", "testDomain")
        val resolvedConversationId = ConversationId("resolvedMLSConversationId", "anotherDomain")
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = originalConversationId
        )
        val (arrangement, oneOnOneMigrator) = arrange {
            withResolveConversationReturning(Either.Right(resolvedConversationId))
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(originalConversationId)))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateOneOnOneConversationReturning(Either.Right(Unit))
        }

        oneOnOneMigrator.migrateToMLS(user)
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(user.id), eq(resolvedConversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenExistingTeamOneOnOne_whenMigratingToProteus_thenShouldNOTCreateGroupConversation() = runTest {
        val user = TestUser.OTHER.copy(
            activeOneOnOneConversationId = null
        )

        val (arrangement, oneOneMigrator) = arrange {
            withGetOneOnOneConversationsWithOtherUserReturning(Either.Right(listOf(TestConversation.ONE_ON_ONE().id)))
            withUpdateOneOnOneConversationReturning(Either.Right(Unit))
        }

        oneOneMigrator.migrateExistingProteus(user)
            .shouldSucceed()

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(null), eq(listOf(TestUser.OTHER.id)), eq(ConversationOptions()))
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(TestUser.OTHER.id), eq(TestConversation.ONE_ON_ONE().id))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        MLSOneOnOneConversationResolverArrangement by MLSOneOnOneConversationResolverArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        ConversationGroupRepositoryArrangement by ConversationGroupRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl() {
        fun arrange() = run {
            block()
            this@Arrangement to OneOnOneMigratorImpl(
                getResolvedMLSOneOnOne = mlsOneOnOneConversationResolver,
                conversationGroupRepository = conversationGroupRepository,
                conversationRepository = conversationRepository,
                messageRepository = messageRepository,
                userRepository = userRepository,
                systemMessageInserter = systemMessageInserter
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()
    }
}
