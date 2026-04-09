package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.ConversationPersistenceApi
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.persistConversations(
                matching { list -> list.any { it.id.value == CONVERSATION_RESPONSE.id.value } }
            )
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.persistConversations(
                matching { list ->
                    list.any {
                        it.id.value == mlsConversation.id.value &&
                                it.protocolInfo is ConversationEntity.ProtocolInfo.MLS &&
                                (it.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState == ConversationEntity.GroupState.PENDING_JOIN
                    }
                }
            )
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.insertConversations(any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.conversationExists("groupId")
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.persistConversations(
                matching { list ->
                    list.any {
                        it.id.value == mlsConversation.id.value &&
                                it.protocolInfo is ConversationEntity.ProtocolInfo.MLS &&
                                (it.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState == ConversationEntity.GroupState.ESTABLISHED
                    }
                }
            )
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {

        val conversationRepository = mock<ConversationRepository>()
        val selfTeamIdProvider = mock<SelfTeamIdProvider>()
        val mlsContext = mock<MlsCoreCryptoContext>()
        val transactionContext = mock<CryptoTransactionContext>()

        init {
            every { transactionContext.mls } returns mlsContext
        }

        suspend fun withMLSGroupEstablished(exists: Boolean) = apply {
            every { transactionContext.mls } returns mlsContext
            everySuspend { mlsContext.conversationExists(any()) } returns exists
        }

        suspend fun withDisabledMLSClient() = apply {
            every { transactionContext.mls } returns null
        }

        suspend fun withConversationInsertSuccess() = apply {
            everySuspend { conversationRepository.persistConversations(any()) } returns Either.Right(Unit)
        }

        suspend fun withUpdateMembers() = apply {
            everySuspend {
                conversationRepository.updateConversationMembers(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun arrange(): Pair<Arrangement, PersistConversationsUseCase> {
            block()
            everySuspend { selfTeamIdProvider.invoke() } returns Either.Right(TeamId("selfTeamId"))
            return this to PersistConversationsUseCaseImpl(
                selfUserId = TestUser.SELF.id,
                conversationRepository = conversationRepository,
                selfTeamIdProvider = selfTeamIdProvider,
            )
        }
    }
}
