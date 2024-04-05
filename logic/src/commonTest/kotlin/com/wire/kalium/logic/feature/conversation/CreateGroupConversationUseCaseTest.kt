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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.fun1
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreateGroupConversationUseCaseTest {

    @Test
    fun givenSyncFails_whenCreatingGroupConversation_thenShouldReturnSyncFailure() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS, creatorClientId = creatorClientId)

        val (_, createGroupConversation) = Arrangement()
            .withWaitingForSyncFailing()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<CreateGroupConversationUseCase.Result.SyncFailure>(result)
    }

    @Test
    fun givenParametersAndEverythingSucceeds_whenCreatingGroupConversation_thenShouldReturnSuccessWithCreatedConversation() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS, creatorClientId = creatorClientId)

        val createdConversation = TestConversation.GROUP()
        val (_, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(createdConversation)
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<CreateGroupConversationUseCase.Result.Success>(result)
        assertEquals(createdConversation, result.conversation)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenRepositoryCreateGroupShouldBeCalled() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS, creatorClientId = creatorClientId)

        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        verify(arrangement.refreshUsersWithoutMetadata)
            .suspendFunction(arrangement.refreshUsersWithoutMetadata::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(name), eq(members), eq(conversationOptions))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncSucceedsAndCreationFails_whenCreatingGroupConversation_thenShouldReturnUnknownWithRootCause() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS, creatorClientId = creatorClientId)

        val rootCause = StorageFailure.DataNotFound
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationFailingWith(rootCause)
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<CreateGroupConversationUseCase.Result.UnknownFailure>(result)
        assertEquals(rootCause, result.cause)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenConversationModifiedDateIsUpdated() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(protocol = ConversationOptions.Protocol.MLS, creatorClientId = creatorClientId)

        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), matching { it.wasInTheLastSecond })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenPersistSystemMessageForReceiptMode() = runTest {
        // given
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = ConversationOptions(
            protocol = ConversationOptions.Protocol.PROTEUS,
            creatorClientId = creatorClientId,
            readReceiptsEnabled = true
        )

        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        // when
        createGroupConversation(name, members, conversationOptions)

        // then
        verify(arrangement.newGroupConversationSystemMessagesCreator)
            .suspendFunction(arrangement.newGroupConversationSystemMessagesCreator::conversationReadReceiptStatus, fun1<Conversation>())
            .with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        @Mock
        val refreshUsersWithoutMetadata = mock(RefreshUsersWithoutMetadataUseCase::class)

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val syncManager = configure(mock(SyncManager::class)) {
            stubsUnitByDefault = true
        }

        @Mock
        val newGroupConversationSystemMessagesCreator = mock(classOf<NewGroupConversationSystemMessagesCreator>())

        private val createGroupConversation = CreateGroupConversationUseCase(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata
        )

        fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))

        fun withWaitingForSyncFailing() = withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        private fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .then { result }
        }

        fun withCreateGroupConversationFailingWith(coreFailure: CoreFailure) =
            withCreateGroupConversationReturning(Either.Left(coreFailure))

        fun withCreateGroupConversationReturning(conversation: Conversation) =
            withCreateGroupConversationReturning(Either.Right(conversation))

        private fun withCreateGroupConversationReturning(result: Either<CoreFailure, Conversation>) = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::createGroupConversation)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withUpdateConversationModifiedDateSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationModifiedDate)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withPersistingReadReceiptsSystemMessage() = apply {
            given(newGroupConversationSystemMessagesCreator)
                .suspendFunction(newGroupConversationSystemMessagesCreator::conversationReadReceiptStatus, fun1<Conversation>())
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to createGroupConversation
    }

}
