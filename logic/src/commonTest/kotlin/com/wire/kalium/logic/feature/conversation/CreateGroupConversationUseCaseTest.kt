package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.CurrentClientIdProvider
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
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
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
            .arrange()

        createGroupConversation(name, members, conversationOptions)

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
            .arrange()

        createGroupConversation(name, members, conversationOptions)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationModifiedDate)
            .with(any(), matching { it.toInstant().wasInTheLastSecond })
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
            .withPersistingSystemMessage()
            .arrange()

        // when
        createGroupConversation(name, members, conversationOptions)

        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                val content = it.content as MessageContent.NewConversationReceiptMode
                content.receiptMode
            })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val syncManager = configure(mock(SyncManager::class)) {
            stubsUnitByDefault = true
        }

        private val createGroupConversation = CreateGroupConversationUseCase(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            TestUser.SELF.id,
            persistMessage
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

        fun withPersistingSystemMessage() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to createGroupConversation
    }

}
