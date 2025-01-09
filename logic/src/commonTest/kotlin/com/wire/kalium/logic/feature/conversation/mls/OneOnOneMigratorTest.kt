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
import kotlinx.coroutines.runBlocking
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

        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.conversationGroupRepository.createGroupConversation(
                name = eq<String?>(null),
                usersList = eq(listOf(TestUser.OTHER.id)),
                options = eq(ConversationOptions())
            )
        }.wasInvoked()

        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(eq(TestUser.OTHER.id), eq(TestConversation.ONE_ON_ONE().id))
        }.wasInvoked()
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

        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(any(), any())
        }.wasNotInvoked()

<<<<<<< HEAD
        coVerify {
            arrangement.messageRepository.moveMessagesToAnotherConversation(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasNotInvoked()
=======
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasNotInvoked()
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
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

<<<<<<< HEAD
        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasNotInvoked()
=======
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasNotInvoked()
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
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

<<<<<<< HEAD
        coVerify {
            arrangement.messageRepository.moveMessagesToAnotherConversation(eq(originalConversationId), eq(resolvedConversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasInvoked(exactly = once)
=======
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(eq(originalConversationId), eq(resolvedConversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
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

<<<<<<< HEAD
        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(eq(user.id), eq(resolvedConversationId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), any())
        }.wasInvoked(exactly = once)
=======
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(user.id), eq(resolvedConversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
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

<<<<<<< HEAD
        coVerify {
            arrangement.conversationGroupRepository.createGroupConversation(
                name = eq<String?>(null),
                usersList = eq(listOf(TestUser.OTHER.id)),
                options = eq(ConversationOptions())
            )
        }.wasNotInvoked()

        coVerify {
            arrangement.userRepository.updateActiveOneOnOneConversation(eq(TestUser.OTHER.id), eq(TestConversation.ONE_ON_ONE().id))
        }.wasInvoked()
=======
        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(null), eq(listOf(TestUser.OTHER.id)), eq(ConversationOptions()))
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateActiveOneOnOneConversation)
            .with(eq(TestUser.OTHER.id), eq(TestConversation.ONE_ON_ONE().id))
            .wasInvoked(exactly = once)
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        MLSOneOnOneConversationResolverArrangement by MLSOneOnOneConversationResolverArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        ConversationGroupRepositoryArrangement by ConversationGroupRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl() {
<<<<<<< HEAD
        fun arrange() = run {
            runBlocking { block() }
=======
        suspend fun arrange() = run {
            block()
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
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
<<<<<<< HEAD
        fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()
=======
        suspend fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()
>>>>>>> f9fcff1f31 (fix: port migration 1 on 1 resolution for mls migration (WPB-15191) (WPB-11194) (#3221))
    }
}
