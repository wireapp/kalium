package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ConversationPersistenceApi::class)
class PersistConversationsUseCaseTest {

    @Test
    fun whenConversationIsNew_shouldPersistIt() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationInsertSuccess()
            withUpdateMembers()
            withMLSGroupEstablished(false)
        }

        useCase(listOf(CONVERSATION_RESPONSE), invalidateMembers = true, originatedFromEvent = true)

        coVerify {
            arrangement.conversationRepository.persistConversations(
                matches { list -> list.any { it.id.value == CONVERSATION_RESPONSE.id.value } }
            )
        }.wasInvoked(once)
    }

    @Test
    fun whenMLSClientUnavailable_shouldFallbackToPendingJoinGroupStateAndPersist() = runTest {
        val mlsConversation = CONVERSATION_RESPONSE.copy(
            groupId = "groupId",
            protocol = ConvProtocol.MLS,
            mlsCipherSuiteTag = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.cipherSuiteTag
        )

        val (arrangement, useCase) = arrange {
            withDisabledMLSClient()
            withUpdateMembers()
            withConversationInsertSuccess()
        }

        useCase(listOf(mlsConversation), invalidateMembers = true, originatedFromEvent = true)

        coVerify {
            arrangement.conversationRepository.persistConversations(
                matches { list ->
                    list.any {
                        it.id.value == mlsConversation.id.value &&
                                it.protocolInfo is ConversationEntity.ProtocolInfo.MLS &&
                                (it.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState == ConversationEntity.GroupState.PENDING_JOIN
                    }
                }
            )
        }.wasInvoked(once)
    }

    @Test
    fun whenConversationAlreadyExists_shouldNotInsertAgain() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationInsertSuccess()
            withUpdateMembers()
        }

        useCase(listOf(CONVERSATION_RESPONSE), invalidateMembers = true, originatedFromEvent = false)

        coVerify { arrangement.conversationRepository.insertConversations(any()) }.wasNotInvoked()
    }

    @Test
    fun whenMlsConversationFromEvent_shouldQueryMLSGroupExistence_andPersistWithCorrectGroupState() = runTest {
        val mlsConversation = CONVERSATION_RESPONSE.copy(
            groupId = "groupId",
            protocol = ConvProtocol.MLS,
            mlsCipherSuiteTag = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.cipherSuiteTag
        )

        val (arrangement, useCase) = arrange {
            withMLSGroupEstablished(true)
            withConversationInsertSuccess()
            withUpdateMembers()
        }

        useCase(listOf(mlsConversation), invalidateMembers = true, originatedFromEvent = true)

        coVerify { arrangement.mlsClient.conversationExists(eq("groupId")) }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.persistConversations(
                matches { list ->
                    list.any {
                        it.id.value == mlsConversation.id.value &&
                                it.protocolInfo is ConversationEntity.ProtocolInfo.MLS &&
                                (it.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState == ConversationEntity.GroupState.ESTABLISHED
                    }
                }
            )
        }.wasInvoked(once)
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        val mlsClientProvider = mock(MLSClientProvider::class)
        val mlsClient = mock(MLSClient::class)
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        suspend fun withMLSGroupEstablished(exists: Boolean) = apply {
            coEvery { mlsClientProvider.getMLSClient() } returns Either.Right(mlsClient)
            coEvery { mlsClient.conversationExists(any()) } returns exists
        }

        suspend fun withDisabledMLSClient() = apply {
            coEvery { mlsClientProvider.getMLSClient() } returns Either.Left(CoreFailure.Unknown(null))
        }

        suspend fun withConversationInsertSuccess() = apply {
            coEvery { conversationRepository.persistConversations(any()) } returns Either.Right(Unit)
        }

        suspend fun withUpdateMembers() = apply {
            coEvery {
                conversationRepository.updateConversationMembers(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun arrange(): Pair<Arrangement, PersistConversationsUseCase> {
            runBlocking { block() }
            coEvery { selfTeamIdProvider() } returns Either.Right(TeamId("selfTeamId"))
            return this to PersistConversationsUseCaseImpl(
                selfUserId = TestUser.SELF.id,
                conversationRepository = conversationRepository,
                selfTeamIdProvider = selfTeamIdProvider,
                mlsClientProvider = mlsClientProvider
            )
        }
    }
}
