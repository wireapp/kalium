package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.anyInstanceOf
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSConversationRepositoryTest {
    @Test
    fun givenConversation_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndWelcomeMessageIsSent() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetConversationByGroupIdSuccessful()
            .withGetAllMembersSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withCreateMLSConversationSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(Arrangement.MLS_CLIENT)
            .function(Arrangement.MLS_CLIENT::createConversation)
            .with(eq(Arrangement.GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.HANDSHAKE)) }
            .wasInvoked(once)
    }

    @Test
    fun givenExistingConversation_whenCallingEstablishMLSGroupFromWelcome_ThenGroupIsCreatedAndGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withProcessWelcomeMessageSuccessful()
            .withGetConversationByGroupIdSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        mlsConversationRepository.establishMLSGroupFromWelcome(Arrangement.WELCOME_EVENT).shouldSucceed()

        verify(Arrangement.MLS_CLIENT)
            .function(Arrangement.MLS_CLIENT::processWelcomeMessage)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonExistingConversation_whenCallingEstablishMLSGroupFromWelcome_ThenGroupIsCreatedButConversationIsNotInserted() = runTest {
        val (_, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withProcessWelcomeMessageSuccessful()
            .withGetConversationByGroupIdFailing()
            .arrange()

        mlsConversationRepository.establishMLSGroupFromWelcome(Arrangement.WELCOME_EVENT).shouldSucceed()

        verify(Arrangement.MLS_CLIENT)
            .function(Arrangement.MLS_CLIENT::processWelcomeMessage)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)
    }

    @Test
    fun givenConversation_whenCallingAddMemberToMLSGroup_thenCommitAndWelcomeMessagesAreSent() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageSuccessful()
            .withInsertMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(Arrangement.MLS_CLIENT)
            .function(Arrangement.MLS_CLIENT::addMember)
            .with(eq(Arrangement.GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.HANDSHAKE)) }
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::insertMembers, fun2<List<Member>, String>())
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenCallingRequestToJoinGroup_ThenGroupStateIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withJoinConversationSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.requestToJoinGroup(Arrangement.GROUP_ID, Arrangement.EPOCH)
        result.shouldSucceed()

        verify(Arrangement.MLS_CLIENT)
            .function(Arrangement.MLS_CLIENT::joinConversation)
            .with(eq(Arrangement.GROUP_ID), eq(Arrangement.EPOCH))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE), eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    class Arrangement {
        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val mlsMessageApi = mock(classOf<MLSMessageApi>())

        fun withGetConversationByGroupIdSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(TestConversation.ENTITY) }
        }

        fun withGetConversationByGroupIdFailing() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(null) }
        }

        fun withGetAllMembersSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getAllMembers)
                .whenInvokedWith(anything())
                .then { flowOf(MEMBERS) }
        }

        fun withInsertMemberSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, String>())
                .whenInvokedWith(anything(), anything())
                .thenDoNothing()
        }

        fun withClaimKeyPackagesSuccessful() = apply {
            given(keyPackageRepository)
                .suspendFunction(keyPackageRepository::claimKeyPackages)
                .whenInvokedWith(anything())
                .then { Either.Right(listOf(KEY_PACKAGE)) }
        }

        fun withGetMLSClientSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(MLS_CLIENT) }
        }

        fun withCreateMLSConversationSuccessful() = apply {
            given(MLS_CLIENT)
                .function(MLS_CLIENT::createConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Pair(HANDSHAKE, WELCOME))
        }

        fun withAddMLSMemberSuccessful() = apply {
            given(MLS_CLIENT)
                .function(MLS_CLIENT::addMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Pair(HANDSHAKE, WELCOME))
        }

        fun withJoinConversationSuccessful() = apply {
            given(MLS_CLIENT)
                .function(MLS_CLIENT::joinConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(HANDSHAKE)
        }

        fun withProcessWelcomeMessageSuccessful() = apply {
            given(MLS_CLIENT)
                .function(MLS_CLIENT::processWelcomeMessage)
                .whenInvokedWith(anything())
                .thenReturn(GROUP_ID)
        }

        fun withSendWelcomeMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendWelcomeMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withSendMLSMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withUpdateConversationGroupStateSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationGroupState)
                .whenInvokedWith(anything(), anything())
                .thenDoNothing()
        }

        fun arrange() = this to MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO
        )

        internal companion object {
            const val EPOCH = 5UL
            const val GROUP_ID = "groupId"
            val MEMBERS = listOf(Member(TestUser.ENTITY_ID, Member.Role.Member))
            val KEY_PACKAGE = KeyPackageDTO(
                "client1",
                "wire.com",
                "keyPackage",
                "keyPackageRef",
                "user1"
            )
            val MLS_CLIENT = mock(classOf<MLSClient>())
            val WELCOME = "welcome".encodeToByteArray()
            val HANDSHAKE = "handshake".encodeToByteArray()
            val WELCOME_EVENT = Event.Conversation.MLSWelcome(
                "eventId",
                TestConversation.ID,
                TestUser.USER_ID,
                WELCOME.encodeBase64(),
                timestampIso = "2022-03-30T15:36:00.000Z"
            )
        }
    }
}
