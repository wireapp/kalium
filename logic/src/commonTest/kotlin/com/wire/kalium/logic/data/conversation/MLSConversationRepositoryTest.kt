package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.AddMemberCommitBundle
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.DeviceTypeDTO
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
            .withCommitAcceptedSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::createConversation)
            .with(eq(Arrangement.GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.GROUP_ID))
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

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::processWelcomeMessage)
            .with(anyInstanceOf(ByteArray::class))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonExistingConversation_whenCallingEstablishMLSGroupFromWelcome_ThenGroupIsCreatedButConversationIsNotInserted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withProcessWelcomeMessageSuccessful()
            .withGetConversationByGroupIdFailing()
            .arrange()

        mlsConversationRepository.establishMLSGroupFromWelcome(Arrangement.WELCOME_EVENT).shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::processWelcomeMessage)
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
            .withCommitAcceptedSuccessful()
            .withInsertMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::insertMembers)
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

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::joinConversation)
            .with(eq(Arrangement.GROUP_ID), eq(Arrangement.EPOCH))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE), eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenConversation_whenCallingCommitPendingProposals_ThenActionIsForwardedToMLsClient() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenConversation_whenCallingCommitPendingProposals_ThenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenConversation_whenCallingCommitPendingProposals_ThenProposalTimerIsClearedOnSuccess() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withCommitBundleSuccessful()
            .withClearProposalTimerSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::clearProposalTimer)
            .with(eq(Arrangement.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenConversation_whenCallingCommitPendingProposals_ThenProposalTimerIsNotClearedOnFailure() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::clearProposalTimer)
            .with(eq(Arrangement.GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenConversation_whenCallingRemoveMemberFromGroup() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withCommitAcceptedSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::removeMember)
            .with(eq(Arrangement.GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMembersByQualifiedID, fun2<List<QualifiedIDEntity>, String>())
            .with(eq(users.map { arrangement.idMapper.toDaoModel(it) }), eq(Arrangement.GROUP_ID))
            .wasInvoked(once)

    }

    class Arrangement {
        val idMapper: IdMapper = IdMapperImpl()

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val clientApi = mock(ClientApi::class)

        @Mock
        val mlsMessageApi = mock(classOf<MLSMessageApi>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

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

        fun withClearProposalTimerSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::clearProposalTimer)
                .whenInvokedWith(anything())
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
                .then { Either.Right(mlsClient) }
        }

        fun withCreateMLSConversationSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::createConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(ADD_MEMBER_COMMIT_BUNDLE)
        }

        fun withAddMLSMemberSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::addMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(ADD_MEMBER_COMMIT_BUNDLE)
        }

        fun withJoinConversationSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::joinConversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT)
        }

        fun withProcessWelcomeMessageSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::processWelcomeMessage)
                .whenInvokedWith(anything())
                .thenReturn(GROUP_ID)
        }

        fun withCommitAcceptedSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::commitAccepted)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun withCommitPendingProposalsSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::commitPendingProposals)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withSendWelcomeMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendWelcomeMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withSendMLSMessageFailing(failure: KaliumException) = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Error(failure) }
        }

        fun withSendMLSMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withCommitBundleSuccessful() = apply {
            withSendMLSMessageSuccessful()
            withSendWelcomeMessageSuccessful()
            withCommitAcceptedSuccessful()
        }

        fun withRemoveMemberSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::removeMember)
                .whenInvokedWith(anything(), anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withFetchClientsOfUsersSuccessful() = apply {
            given(clientApi)
                .suspendFunction(clientApi::listClientsOfUsers)
                .whenInvokedWith(anything())
                .thenReturn(NetworkResponse.Success(value = CLIENTS_OF_USERS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        fun withDeleteMembersSuccessful() = apply {
            given(clientApi)
                .suspendFunction(clientApi::listClientsOfUsers)
                .whenInvokedWith(anything())
                .thenReturn(NetworkResponse.Success(value = CLIENTS_OF_USERS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        fun withUpdateConversationGroupStateSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMembersByQualifiedID, fun2<List<QualifiedIDEntity>, String>())
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)

        }

        fun arrange() = this to MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO,
            clientApi
        )

        internal companion object {
            const val EPOCH = 5UL
            const val GROUP_ID = "groupId"
            val INVALID_REQUEST_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", ""))
            val MEMBERS = listOf(Member(TestUser.ENTITY_ID, Member.Role.Member))
            val KEY_PACKAGE = KeyPackageDTO(
                "client1",
                "wire.com",
                "keyPackage",
                "keyPackageRef",
                "user1"
            )
            val WELCOME = "welcome".encodeToByteArray()
            val COMMIT = "commit".encodeToByteArray()
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()
            val COMMIT_BUNDLE = CommitBundle(COMMIT, WELCOME, PUBLIC_GROUP_STATE)
            val ADD_MEMBER_COMMIT_BUNDLE = AddMemberCommitBundle(COMMIT, WELCOME, PUBLIC_GROUP_STATE)
            val WELCOME_EVENT = Event.Conversation.MLSWelcome(
                "eventId",
                TestConversation.ID,
                TestUser.USER_ID,
                WELCOME.encodeBase64(),
                timestampIso = "2022-03-30T15:36:00.000Z"
            )
            private val SIMPLE_CLIENT_RESPONSE = SimpleClientResponse("an ID", DeviceTypeDTO.Desktop)

            val CLIENTS_OF_USERS_RESPONSE = mapOf(TestUser.NETWORK_ID to listOf(SIMPLE_CLIENT_RESPONSE))
        }
    }
}
