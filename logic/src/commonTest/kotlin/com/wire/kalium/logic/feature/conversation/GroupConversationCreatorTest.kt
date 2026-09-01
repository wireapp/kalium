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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.conversation.CreateGroupConversationResult
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.GroupConversationCreatorImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.wasInTheLastSecond
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.refreshUsersWithoutMetadata.invoke()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationGroupRepository.createGroupConversationWithPendingResult(
                eq(name),
                eq(members),
                eq(conversationOptions)
            )
        }
    }

    @Test
    fun givenSyncSucceedsAndCreationFails_whenCreatingGroupConversation_thenShouldReturnUnknownWithRootCause() = runTest {
        val name = "Conv Name"
        val creatorClientId = ClientId("ClientId")
        val members = listOf(TestUser.USER_ID, TestUser.OTHER.id)
        val conversationOptions = CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS, creatorClientId = creatorClientId)

        val rootCause = StorageFailure.DataNotFound
        val (_, createGroupConversation) = Arrangement()
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
    fun givenInitialCreationFailsWithBackendConflict_whenCreatingGroupConversation_thenReturnsBackendConflict() = runTest {
        val domains = listOf("backend-a.example", "backend-b.example")
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withCurrentClientIdReturning(ClientId("client-id"))
            .withMarkingConversationDeletedLocallySucceeding()
            .withCreateGroupConversationFailingWith(
                MLSFailure.FederatedBackendConflict(domains),
                TestConversation.ID,
            )
            .arrange()

        val result = createGroupConversation(
            "Conversation name",
            listOf(TestUser.USER_ID, TestUser.OTHER_USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
        )

        val conflict = assertIs<ConversationCreationResult.BackendConflictFailure>(result)
        assertEquals(domains, conflict.domains)
        assertNull(conflict.conversationId)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(TestConversation.ID, true)
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(TestConversation.ID))
        }
    }

    @Test
    fun givenInitialBackendConflictAndCleanupFails_whenCreatingGroupConversation_thenReturnsCleanupFallbackId() = runTest {
        val domains = listOf("backend-a.example", "backend-b.example")
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withCurrentClientIdReturning(ClientId("client-id"))
            .withMarkingConversationDeletedLocallyFailing()
            .withCreateGroupConversationFailingWith(
                MLSFailure.FederatedBackendConflict(domains),
                TestConversation.ID,
            )
            .arrange()

        val result = createGroupConversation(
            "Conversation name",
            listOf(TestUser.USER_ID, TestUser.OTHER_USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
        )

        val conflict = assertIs<ConversationCreationResult.BackendConflictFailure>(result)
        assertEquals(domains, conflict.domains)
        assertEquals(TestConversation.ID, conflict.conversationId)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(TestConversation.ID, true)
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(TestConversation.ID))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationModifiedDate(any(), matching { it.wasInTheLastSecond })
        }
    }

    @Test
    fun givenNameMembersAndOptions_whenCreatingGroupConversation_thenPersistSystemMessageForReceiptMode() = runTest {
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

        createGroupConversation(name, members, conversationOptions)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<Conversation>())
        }
    }

    @Test
    fun givenMLSGroupEstablishFails_whenCreatingGroupConversation_thenPendingConversationIsReturned() = runTest {
        val conversation = TestConversation.GROUP(TestConversation.MLS_PROTOCOL_INFO)
        val rootCause = StorageFailure.DataNotFound
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withCurrentClientIdReturning(ClientId("client-id"))
            .withMarkingConversationDeletedLocallySucceeding()
            .withCreateGroupConversationReturning(
                CreateGroupConversationResult.PendingMLSGroupCreation(conversation.id, rootCause)
            )
            .arrange()

        val result = createGroupConversation(
            conversation.name.orEmpty(),
            listOf(TestUser.USER_ID),
            CreateConversationParam(protocol = CreateConversationParam.Protocol.MLS)
        )

        assertIs<ConversationCreationResult.PendingMLSGroupCreation>(result)
        assertEquals(conversation.id, result.conversationId)
        assertEquals(rootCause, result.cause)
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.setConversationDeletedLocally(any(), any())
        }
    }

    @Test
    fun givenPendingMLSGroup_whenRetryingCreation_thenExistingConversationIsEstablished() = runTest {
        val conversation = TestConversation.GROUP(
            TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION
            )
        )
        val establishedConversation = conversation.copy(
            protocol = (conversation.protocol as Conversation.ProtocolInfo.MLS).copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
            )
        )
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withConversationsReturning(conversation, establishedConversation)
            .withJoiningExistingMLSConversationSucceeding()
            .withUpdateConversationModifiedDateSucceeding()
            .withPersistingReadReceiptsSystemMessage()
            .withTransactionInvokingBlock()
            .arrange()

        val result = createGroupConversation.retryPendingMLSGroupCreation(conversation.id)

        assertIs<ConversationCreationResult.Success>(result)
        assertEquals(conversation.id, result.conversation.id)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversation.invoke(any(), conversation.id)
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversation.id))
        }
    }

    @Test
    fun givenJoinReturnsSuccessButConversationIsStillPending_whenRetryingCreation_thenRecoveryRemainsQueued() = runTest {
        val conversation = TestConversation.GROUP(
            TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION
            )
        )
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withConversationReturning(conversation)
            .withJoiningExistingMLSConversationSucceeding()
            .withTransactionInvokingBlock()
            .arrange()

        val result = createGroupConversation.retryPendingMLSGroupCreation(conversation.id)

        assertIs<ConversationCreationResult.UnknownFailure>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(any())
        }
    }

    @Test
    fun givenJoinFailsWithBackendConflict_whenRetryingCreation_thenReturnsConflictAndAcknowledgesPendingAction() = runTest {
        val conversation = TestConversation.GROUP(
            TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION
            )
        )
        val domains = listOf("backend-a.example", "backend-b.example")
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withConversationReturning(conversation)
            .withJoiningExistingMLSConversationReturning(Either.Left(MLSFailure.FederatedBackendConflict(domains)))
            .withMarkingConversationDeletedLocallySucceeding()
            .withTransactionInvokingBlock()
            .arrange()

        val result = createGroupConversation.retryPendingMLSGroupCreation(conversation.id)

        val conflict = assertIs<ConversationCreationResult.BackendConflictFailure>(result)
        assertEquals(domains, conflict.domains)
        assertNull(conflict.conversationId)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(conversation.id, true)
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversation.id))
        }
    }

    @Test
    fun givenTerminallyFailedMLSGroup_whenDiscardingCreation_thenConversationIsHiddenAndPendingActionIsAcknowledged() = runTest {
        val conversationId = TestConversation.ID
        val (arrangement, createGroupConversation) = Arrangement()
            .withMarkingConversationDeletedLocallySucceeding()
            .arrange()

        createGroupConversation.discardPendingMLSGroupCreation(conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(conversationId, true)
            arrangement.pendingActionsRepository.acknowledgePendingMLSGroupJoins(listOf(conversationId))
        }
    }

    @Test
    fun givenMLSGroupWasAlreadyRecovered_whenRetryingCreation_thenEstablishIsNotRepeated() = runTest {
        val conversation = TestConversation.GROUP(
            TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
            )
        )
        val (arrangement, createGroupConversation) = Arrangement()
            .withWaitingForSyncSucceeding()
            .withConversationReturning(conversation)
            .withUpdateConversationModifiedDateSucceeding()
            .withPersistingReadReceiptsSystemMessage()
            .arrange()

        val result = createGroupConversation.retryPendingMLSGroupCreation(conversation.id)

        assertIs<ConversationCreationResult.Success>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversation.invoke(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val conversationGroupRepository = mock<ConversationGroupRepository>(mode = MockMode.autoUnit)
        val refreshUsersWithoutMetadata = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)
        val newGroupConversationSystemMessagesCreator = mock<NewGroupConversationSystemMessagesCreator>(mode = MockMode.autoUnit)
        val joinExistingMLSConversation = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)
        val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)

        private val createGroupConversation = GroupConversationCreatorImpl(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata,
            cryptoTransactionProvider,
            joinExistingMLSConversation,
            pendingActionsRepository,
        )

        suspend fun withWaitingForSyncSucceeding() = withSyncReturning(Either.Right(Unit))

        suspend fun withWaitingForSyncFailing() = withSyncReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        suspend fun withForbiddenFailure() = withSyncReturning(
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(
                            code = 403,
                            label = "operation-denied",
                            message = "Invalid-permission"
                        )
                    )
                )
            )
        )

        private suspend fun withSyncReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                syncManager.waitUntilLiveOrFailure()
            } returns result
        }

        suspend fun withCreateGroupConversationFailingWith(
            coreFailure: CoreFailure,
            conversationId: ConversationId? = null,
        ) = withCreateGroupConversationReturning(CreateGroupConversationResult.Failure(coreFailure, conversationId))

        suspend fun withCreateGroupConversationReturning(conversation: Conversation) =
            withCreateGroupConversationReturning(CreateGroupConversationResult.Success(conversation))

        suspend fun withCreateGroupConversationReturning(result: CreateGroupConversationResult) = apply {
            everySuspend {
                conversationGroupRepository.createGroupConversationWithPendingResult(any(), any(), any())
            } returns result
        }

        suspend fun withConversationReturning(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.getConversationById(conversation.id)
            } returns Either.Right(conversation)
        }

        suspend fun withConversationsReturning(vararg conversations: Conversation) = apply {
            everySuspend {
                conversationRepository.getConversationById(conversations.first().id)
            } sequentiallyReturns conversations.map { Either.Right(it) }
        }

        suspend fun withJoiningExistingMLSConversationSucceeding() = apply {
            withJoiningExistingMLSConversationReturning(Either.Right(Unit))
        }

        suspend fun withJoiningExistingMLSConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                joinExistingMLSConversation.invoke(any(), any())
            } returns result
        }

        suspend fun withTransactionInvokingBlock() = apply {
            withTransactionReturning(Either.Right(Unit))
        }

        suspend fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
        }

        suspend fun withUpdateConversationModifiedDateSucceeding() = apply {
            everySuspend {
                conversationRepository.updateConversationModifiedDate(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withMarkingConversationDeletedLocallySucceeding() = apply {
            everySuspend {
                conversationRepository.setConversationDeletedLocally(any(), eq(true))
            } returns Either.Right(Unit)
        }

        suspend fun withMarkingConversationDeletedLocallyFailing() = apply {
            everySuspend {
                conversationRepository.setConversationDeletedLocally(any(), eq(true))
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withPersistingReadReceiptsSystemMessage() = apply {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationReadReceiptStatus(any<Conversation>())
            } returns Either.Right(Unit)
        }

        fun arrange() = this to createGroupConversation
    }

}
