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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.GroupConversationCreatorImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupConversationCreatorTest {

    @Test
    fun givenSyncFails_whenCreatingGroupConversation_thenShouldReturnSyncFailure() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val (_, createGroupConversation) = Arrangement()
            .withWaitingForSyncFailing()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<ConversationCreationResult.SyncFailure>(result)
    }

    @Test
    fun givenInvalidPermission_whenCreatingGroupConversation_thenShouldReturnForbiddenFailure() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val (_, createGroupConversation) = Arrangement()
            .withForbiddenFailure()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<ConversationCreationResult.Forbidden>(result)
    }

    @Test
    fun givenParametersAndEverythingSucceeds_whenCreatingGroupConversation_thenShouldReturnSuccessWithCreatedConversation() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val createdConversation = TestConversation.GROUP()
        val (_, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(createdConversation)
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<ConversationCreationResult.Success>(result)
        assertEquals(createdConversation, result.conversation)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenRepositoryCreateGroupShouldBeCalled() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        coVerify {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.createGroupConversation(eq(name), eq(members), eq(conversationOptions))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncSucceedsAndCreationFails_whenCreatingGroupConversation_thenShouldReturnUnknownWithRootCause() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val rootCause = StorageFailure.DataNotFound
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationFailingWith(rootCause)
            .arrange()

        val result = createGroupConversation(name, members, conversationOptions)

        assertIs<ConversationCreationResult.UnknownFailure>(result)
        assertEquals(rootCause, result.cause)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenConversationModifiedDateIsUpdated() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withCurrentClientIdReturning(creatorClientId)
            .withCreateGroupConversationReturning(TestConversation.GROUP())
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        coVerify {
            arrangement.conversationRepository.updateConversationModifiedDate(any(), matches { it.wasInTheLastSecond })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenPersistSystemMessageForReceiptMode() = runTest {
        // given
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(
            protocol = CreateConversationParam.Protocol.PROTEUS,
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
        coVerify {
            arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<Conversation>())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val conversationRepository = mock(ConversationRepository::class)
        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        val refreshUsersWithoutMetadata = mock(RefreshUsersWithoutMetadataUseCase::class)
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)
        val syncManager = mock(SyncManager::class)
        val newGroupConversationSystemMessagesCreator = mock(NewGroupConversationSystemMessagesCreator::class)

        private val createGroupConversation = GroupConversationCreatorImpl(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata
        )

        suspend fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))

        suspend fun withWaitingForSyncFailing() = withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        suspend fun withForbiddenFailure() = withSyncReturning(
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            code = 403,
                            label = "operation-denied",
                            message = "Invalid-permission"
                        )
                    )
                )
            )
        )

        private suspend fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncManager.waitUntilLiveOrFailure()
            }.returns(result)
        }

        suspend fun withCreateGroupConversationFailingWith(coreFailure: CoreFailure) =
            withCreateGroupConversationReturning(Either.Left(coreFailure))

        suspend fun withCreateGroupConversationReturning(conversation: Conversation) =
            withCreateGroupConversationReturning(Either.Right(conversation))

        private suspend fun withCreateGroupConversationReturning(result: Either<CoreFailure, Conversation>) = apply {
            coEvery {
                conversationGroupRepository.createGroupConversation(any(), any(), any())
            }.returns(result)
        }

        suspend fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withUpdateConversationModifiedDateSucceeding() = apply {
            coEvery {
                conversationRepository.updateConversationModifiedDate(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withPersistingReadReceiptsSystemMessage() = apply {
            coEvery {
                newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<Conversation>())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to createGroupConversation
    }

}
