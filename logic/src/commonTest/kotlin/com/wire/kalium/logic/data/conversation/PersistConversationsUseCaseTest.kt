package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
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
import io.mockative.every
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

        useCase(
            arrangement.transactionContext,
            listOf(CONVERSATION_RESPONSE),
            invalidateMembers = true,
            reason = ConversationSyncReason.Event,
        )

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

        useCase(
            arrangement.transactionContext,
            listOf(mlsConversation),
            invalidateMembers = true,
            reason = ConversationSyncReason.Event,
        )

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

        useCase(
            arrangement.transactionContext,
            listOf(CONVERSATION_RESPONSE),
            invalidateMembers = true,
            reason = ConversationSyncReason.Other
        )

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

        useCase(
            arrangement.transactionContext,
            listOf(mlsConversation),
            invalidateMembers = true,
            reason = ConversationSyncReason.Event
        )

        coVerify { arrangement.mlsContext.conversationExists(eq("groupId")) }.wasInvoked(once)

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

        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val mlsContext: MlsCoreCryptoContext = mock(MlsCoreCryptoContext::class)
        val transactionContext = mock(CryptoTransactionContext::class)

        init {
            every { transactionContext.mls } returns mlsContext
        }

        suspend fun withMLSGroupEstablished(exists: Boolean) = apply {
            every { transactionContext.mls } returns mlsContext
            coEvery { mlsContext.conversationExists(any()) } returns exists
        }

        suspend fun withDisabledMLSClient() = apply {
            every { transactionContext.mls } returns null
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
            )
        }
    }
}
