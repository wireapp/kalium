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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.mls.MLSOneOnOneConversationResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSOneOnOneConversationResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSConnectionMigratorTest {

    @Test
    fun givenConnectionIsAlreadyMLS_whenMigrating_thenShouldNotDoAnythingElseAndSucceed() = runTest {
        val connection = TestConnection.CONNECTION.copy(
            conversationId = TestConversation.ID.value,
            qualifiedConversationId = TestConversation.ID
        )

        val (arrangement, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldSucceed()

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::updateConversationForConnection)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenResolvingMLSConversationFails_whenMigrating_thenShouldPropagateFailure() = runTest {
        val connection = TestConnection.CONNECTION.copy(
            conversationId = "someRandomConversationId",
            qualifiedConversationId = ConversationId("someRandomConversationId", "testDomain")
        )
        val failure = CoreFailure.MissingClientRegistration

        val (_, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Left(failure))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenMigratingMessagesFails_whenMigrating_thenShouldPropagateFailureAndNotUpdateConversation() = runTest {
        val failure = StorageFailure.DataNotFound
        val connection = TestConnection.CONNECTION.copy(
            conversationId = "someRandomConversationId",
            qualifiedConversationId = ConversationId("someRandomConversationId", "testDomain")
        )
        val (arrangement, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
            withMoveMessagesToAnotherConversation(Either.Left(failure))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldFail {
                assertEquals(failure, it)
            }

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::updateConversationForConnection)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenUpdatingConnectionFails_whenMigrating_thenShouldPropagateFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val connection = TestConnection.CONNECTION.copy(
            conversationId = "someRandomConversationId",
            qualifiedConversationId = ConversationId("someRandomConversationId", "testDomain")
        )
        val (_, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Right(TestConversation.ID))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateConversationForConnectionReturning(Either.Left(failure))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenResolvedMLSConversation_whenMigrating_thenShouldMoveMessagesCorrectly() = runTest {
        val originalConversationId = ConversationId("someRandomConversationId", "testDomain")
        val resolvedConversationId = ConversationId("resolvedMLSConversationId", "anotherDomain")
        val connection = TestConnection.CONNECTION.copy(
            conversationId = originalConversationId.value,
            qualifiedConversationId = originalConversationId
        )
        val (arrangement, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Right(resolvedConversationId))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateConversationForConnectionReturning(Either.Right(Unit))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldSucceed()

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::moveMessagesToAnotherConversation)
            .with(eq(originalConversationId), eq(resolvedConversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenResolvedMLSConversation_whenUpdatingConversation_thenCallRepositoryWithCorrectArguments() = runTest {
        val originalConversationId = ConversationId("someRandomConversationId", "testDomain")
        val resolvedConversationId = ConversationId("resolvedMLSConversationId", "anotherDomain")
        val connection = TestConnection.CONNECTION.copy(
            conversationId = originalConversationId.value,
            qualifiedConversationId = originalConversationId
        )
        val (arrangement, mlsConnectionMigrator) = arrange {
            withResolveConversationReturning(Either.Right(resolvedConversationId))
            withMoveMessagesToAnotherConversation(Either.Right(Unit))
            withUpdateConversationForConnectionReturning(Either.Right(Unit))
        }

        mlsConnectionMigrator.migrateConnectionToMLS(connection)
            .shouldSucceed()

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::updateConversationForConnection)
            .with(eq(connection.qualifiedToId), eq(resolvedConversationId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        MLSOneOnOneConversationResolverArrangement by MLSOneOnOneConversationResolverArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        ConnectionRepositoryArrangement by ConnectionRepositoryArrangementImpl() {

        fun arrange() = run {
            block()
            this@Arrangement to MLSConnectionMigratorImpl(
                getResolvedMLSOneOnOne = mlsOneOnOneConversationResolver,
                connectionRepository = connectionRepository,
                messageRepository = messageRepository
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()
    }
}
