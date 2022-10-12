package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.AddMemberCommitBundle
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.Ed25519Key
import com.wire.kalium.logic.data.mlspublickeys.KeyType
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKey
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.anyInstanceOf
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSConversationRepositoryTest {
    @Test
    fun givenSuccessfulResponses_whenCallingEstablishMLSGroup_thenGroupIsCreatedAndCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetConversationByGroupIdSuccessful()
            .withGetAllMembersSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withCreateMLSConversationSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageSuccessful()
            .withCommitAcceptedSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::createConversation)
            .with(eq(Arrangement.RAW_GROUP_ID), eq(listOf(Arrangement.CRYPTO_MLS_PUBLIC_KEY)))
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::addMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingEstablishMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetConversationByGroupIdSuccessful()
            .withGetAllMembersSuccessful()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withGetPublicKeysSuccessful()
            .withCreateMLSConversationSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withClearPendingCommitSuccessful()
            .withWaitUntilLiveSuccessful()
            .withCommitAcceptedSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .arrange()

        val result = mlsConversationRepository.establishMLSGroup(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(twice)
    }

    @Test
    fun givenExistingConversation_whenCallingEstablishMLSGroupFromWelcome_thenGroupIsCreatedAndGroupStateIsUpdated() = runTest {
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
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(Arrangement.RAW_GROUP_ID))
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
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
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
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenMemberIsInsertedInDB() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
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

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::insertMembers)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingAddMemberToMLSGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
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
            .suspendFunction(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenMlsClientMismatchError_whenCallingAddMemberToMLSGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withClearPendingCommitSuccessful()
            .withWaitUntilLiveSuccessful()
            .withCommitAcceptedSuccessful()
            .withInsertMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(twice)
    }

    @Test
    fun givenMlsStaleMessageError_whenCallingAddMemberToMLSGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withClearPendingCommitSuccessful()
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .withCommitAcceptedSuccessful()
            .withInsertMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldSucceed()

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(twice)
    }

    @Test
    fun givenNonRecoverableError_whenCallingAddMemberToMLSGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withClaimKeyPackagesSuccessful()
            .withGetMLSClientSuccessful()
            .withAddMLSMemberSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withClearPendingCommitSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .withCommitAcceptedSuccessful()
            .withInsertMemberSuccessful()
            .arrange()

        val result = mlsConversationRepository.addMemberToMLSGroup(Arrangement.GROUP_ID, listOf(TestConversation.USER_ID1))
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRequestToJoinGroup_ThenGroupStateIsUpdated() = runTest {
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
            .with(eq(Arrangement.RAW_GROUP_ID), eq(Arrangement.EPOCH))
            .wasInvoked(once)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE), eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenCommitBundleIsSentAndAccepted() = runTest {
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
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingCommitPendingProposals_thenProposalTimerIsClearedOnSuccess() = runTest {
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
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenProposalTimerIsNotCleared() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withClearPendingCommitSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::clearProposalTimer)
            .with(eq(Arrangement.GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenNonRecoverableError_whenCallingCommitPendingProposals_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withCommitPendingProposalsSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withClearPendingCommitSuccessful()
            .arrange()

        val result = mlsConversationRepository.commitPendingProposals(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withCommitAcceptedSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withDeleteMembersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::removeMember)
            .with(eq(Arrangement.RAW_GROUP_ID), anything())
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenMemberIsRemovedFromDB() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withCommitAcceptedSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withDeleteMembersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMembersByQualifiedID, fun2<List<QualifiedIDEntity>, String>())
            .with(eq(users.map { arrangement.idMapper.toDaoModel(it) }), eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingRemoveMemberFromGroup_thenPendingProposalsAreFirstCommitted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withCommitAcceptedSuccessful()
            .withSendWelcomeMessageSuccessful()
            .withDeleteMembersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitPendingProposals)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingRemoveMemberFromGroup_thenClearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsSuccessful()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withClearPendingCommitSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenClientMismatchError_whenCallingRemoveMemberFromGroup_thenClearCommitAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing()
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageFailing(Arrangement.MLS_CLIENT_MISMATCH_ERROR, times = 1)
            .withSendWelcomeMessageSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withClearPendingCommitSuccessful()
            .withWaitUntilLiveSuccessful()
            .withCommitAcceptedSuccessful()
            .withDeleteMembersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.syncManager)
            .function(arrangement.syncManager::waitUntilLiveOrFailure)
            .with()
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(twice)
    }

    @Test
    fun givenStaleMessageError_whenCallingRemoveMemberFromGroup_thenWaitUntilLiveAndRetry() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withCommitPendingProposalsReturningNothing(times = 1)
            .withGetMLSClientSuccessful()
            .withFetchClientsOfUsersSuccessful()
            .withRemoveMemberSuccessful()
            .withSendMLSMessageFailing(Arrangement.MLS_STALE_MESSAGE_ERROR, times = 1)
            .withSendWelcomeMessageSuccessful()
            .withClearProposalTimerSuccessful()
            .withWaitUntilLiveSuccessful()
            .withUpdateConversationGroupStateSuccessful()
            .withCommitAcceptedSuccessful()
            .withDeleteMembersSuccessful()
            .arrange()

        val users = listOf(TestUser.USER_ID)
        val result = mlsConversationRepository.removeMembersFromMLSGroup(Arrangement.GROUP_ID, users)
        result.shouldSucceed()

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasNotInvoked()

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(twice)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenCommitBundleIsSentAndAccepted() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withCommitBundleSuccessful()
            .withUpdateLastKeyingMaterialTimestampSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.mlsMessageApi).coroutine { sendMessage(MLSMessageApi.Message(Arrangement.COMMIT)) }
            .wasInvoked(once)

        verify(arrangement.mlsMessageApi).coroutine { sendWelcomeMessage(MLSMessageApi.WelcomeMessage(Arrangement.WELCOME)) }
            .wasInvoked(once)

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::commitAccepted)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenSuccessfulResponses_whenCallingUpdateKeyMaterial_thenKeyingMaterialTimestampIsUpdated() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withCommitBundleSuccessful()
            .withUpdateLastKeyingMaterialTimestampSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldSucceed()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateKeyingMaterial)
            .with(eq(Arrangement.RAW_GROUP_ID), anyInstanceOf(Instant::class))
            .wasInvoked(once)
    }

    @Test
    fun givenNonRecoverableError_whenCallingUpdateKeyMaterial_clearCommitAndFail() = runTest {
        val (arrangement, mlsConversationRepository) = Arrangement()
            .withGetMLSClientSuccessful()
            .withUpdateKeyingMaterialSuccessful()
            .withSendMLSMessageFailing(Arrangement.INVALID_REQUEST_ERROR)
            .withClearPendingCommitSuccessful()
            .arrange()

        val result = mlsConversationRepository.updateKeyingMaterial(Arrangement.GROUP_ID)
        result.shouldFail()

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::clearPendingCommit)
            .with(eq(Arrangement.RAW_GROUP_ID))
            .wasInvoked(once)
    }

    class Arrangement {
        val idMapper: IdMapper = IdMapperImpl()

        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val mlsPublicKeysRepository = mock(classOf<MLSPublicKeysRepository>())

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

        @Mock
        val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

        fun withGetConversationByGroupIdSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByGroupID)
                .whenInvokedWith(anything())
                .then { flowOf(TestConversation.VIEW_ENTITY) }
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

        fun withGetPublicKeysSuccessful() = apply {
            given(mlsPublicKeysRepository)
                .suspendFunction(mlsPublicKeysRepository::getKeys)
                .whenInvoked()
                .then { Either.Right(listOf(MLS_PUBLIC_KEY)) }
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
                .thenReturn(Unit)
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
                .thenReturn(RAW_GROUP_ID)
        }

        fun withClearPendingCommitSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::clearPendingCommit)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
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

        fun withCommitPendingProposalsReturningNothing(times: Int = Int.MAX_VALUE) = apply {
            withCommitPendingProposalsSuccessful()
            var invocationCounter = 0
            given(mlsClient)
                .function(mlsClient::commitPendingProposals)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .thenReturn(null)
        }

        fun withUpdateKeyingMaterialSuccessful() = apply {
            given(mlsClient)
                .function(mlsClient::updateKeyingMaterial)
                .whenInvokedWith(anything())
                .thenReturn(COMMIT_BUNDLE)
        }

        fun withSendWelcomeMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendWelcomeMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(Unit, emptyMap(), 201) }
        }

        fun withSendMLSMessageFailing(failure: KaliumException, times: Int = Int.MAX_VALUE) = apply {
            withSendMLSMessageSuccessful()
            var invocationCounter = 0
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times })
                .then { NetworkResponse.Error(failure) }
        }

        fun withSendMLSMessageSuccessful() = apply {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then { NetworkResponse.Success(SendMLSMessageResponse(TIME, emptyList()), emptyMap(), 201) }
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
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMembersByQualifiedID, fun2<List<QualifiedIDEntity>, String>())
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun withUpdateConversationGroupStateSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationGroupState)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun withUpdateLastKeyingMaterialTimestampSuccessful() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateKeyingMaterial)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun withWaitUntilLiveSuccessful() = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            mlsMessageApi,
            conversationDAO,
            clientApi,
            syncManager,
            mlsPublicKeysRepository
        )

        internal companion object {
            const val EPOCH = 5UL
            const val RAW_GROUP_ID = "groupId"
            val TIME = Clock.System.now().toString()
            val GROUP_ID = GroupID(RAW_GROUP_ID)
            val INVALID_REQUEST_ERROR = KaliumException.InvalidRequestError(ErrorResponse(405, "", ""))
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-stale-message"))
            val MLS_CLIENT_MISMATCH_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-client-mismatch"))
            val MEMBERS = listOf(Member(TestUser.ENTITY_ID, Member.Role.Member))
            val MLS_PUBLIC_KEY = MLSPublicKey(
                Ed25519Key("gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw=".decodeBase64Bytes()),
                KeyType.REMOVAL
            )
            val CRYPTO_MLS_PUBLIC_KEY = MapperProvider.mlsPublicKeyMapper().toCrypto(MLS_PUBLIC_KEY)
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
