/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.Conversation.Protocol
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.ConversationPersistenceApi
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

@OptIn(ConversationPersistenceApi::class)
internal class UpdateConversationProtocolUseCaseTest {

    @Test
    fun whenLocalOnlyTrue_callsUpdateProtocolLocally_andReturnsTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolLocallySuccess()
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.MLS, localOnly = true)

        // Then
        assertEquals(Either.Right(true), result)
    }

    @Test
    fun whenRemoteUpdateReturnsHasUpdatedTrue_returnsRightTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolRemotelySuccess(hasUpdated = true)
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.PROTEUS, localOnly = false)

        // Then
        assertEquals(Either.Right(true), result)
    }

    @Test
    fun whenRemoteUpdateReturnsHasUpdatedFalse_persistsConversation_andReturnsTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolRemotelySuccess(hasUpdated = false)
            .withPersistConversationsSuccess()
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.MLS, false)

        // Then
        assertEquals(Either.Right(true), result)
    }

    @Test
    fun givenProtocolChangedToMixedAndGroupMissingInCoreCrypto_whenUpdating_thenGroupIsMarkedPendingWelcomeWithBackendEpoch() =
        runTest {
            val (arrangement, useCase) = Arrangement()
                .withUpdateProtocolLocallySuccess(MIXED_RESPONSE)
                .withConversationExistsInCoreCrypto(false)
                .withUpdateMLSGroupIdAndStateSuccess()
                .arrange()

            val result = useCase(arrangement.transactionContext, MIXED_RESPONSE.id.toModel(), Protocol.MIXED, localOnly = true)

            assertEquals(Either.Right(true), result)
            verifySuspend {
                arrangement.conversationRepository.updateMLSGroupIdAndState(
                    eq(MIXED_RESPONSE.id.toModel()),
                    eq(GroupID(GROUP_ID)),
                    eq(EPOCH),
                    eq(GroupState.PENDING_WELCOME_MESSAGE)
                )
            }
        }

    @Test
    fun givenProtocolChangedToMixedAndGroupExistsInCoreCrypto_whenUpdating_thenGroupIsMarkedEstablished() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolLocallySuccess(MIXED_RESPONSE)
            .withConversationExistsInCoreCrypto(true)
            .withUpdateMLSGroupIdAndStateSuccess()
            .arrange()

        useCase(arrangement.transactionContext, MIXED_RESPONSE.id.toModel(), Protocol.MIXED, localOnly = true)

        verifySuspend {
            arrangement.conversationRepository.updateMLSGroupIdAndState(any(), any(), any(), eq(GroupState.ESTABLISHED))
        }
    }

    @Test
    fun givenProtocolChangedToMixedWithoutMLSClient_whenUpdating_thenGroupIsMarkedPendingJoin() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolLocallySuccess(MIXED_RESPONSE)
            .withoutMLSContext()
            .withUpdateMLSGroupIdAndStateSuccess()
            .arrange()

        useCase(arrangement.transactionContext, MIXED_RESPONSE.id.toModel(), Protocol.MIXED, localOnly = true)

        verifySuspend {
            arrangement.conversationRepository.updateMLSGroupIdAndState(any(), any(), any(), eq(GroupState.PENDING_JOIN))
        }
    }

    @Test
    fun givenProtocolChangedToProteus_whenUpdating_thenMLSGroupStateIsNotTouched() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolLocallySuccess(MIXED_RESPONSE)
            .arrange()

        useCase(arrangement.transactionContext, MIXED_RESPONSE.id.toModel(), Protocol.PROTEUS, localOnly = true)

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateMLSGroupIdAndState(any(), any(), any(), any())
        }
    }

    class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository: ConversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        private val persistConversations: PersistConversationsUseCase = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)

        suspend fun withUpdateProtocolLocallySuccess(response: ConversationResponse = CONVERSATION_RESPONSE) = apply {
            everySuspend {
                conversationRepository.updateProtocolLocally(any(), any())
            }.returns(
                Either.Right(
                    ConversationProtocolUpdateStatus(
                        response = response,
                        hasUpdated = true
                    )
                )
            )
        }

        suspend fun withConversationExistsInCoreCrypto(exists: Boolean) = apply {
            everySuspend { mlsContext.conversationExists(any()) } returns exists
        }

        fun withoutMLSContext() = apply {
            every { transactionContext.mls } returns null
        }

        suspend fun withUpdateMLSGroupIdAndStateSuccess() = apply {
            everySuspend {
                conversationRepository.updateMLSGroupIdAndState(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateProtocolRemotelySuccess(hasUpdated: Boolean) = apply {
            everySuspend {
                conversationRepository.updateProtocolRemotely(any(), any())
            }.returns(
                Either.Right(
                    ConversationProtocolUpdateStatus(
                        response = CONVERSATION_RESPONSE,
                        hasUpdated = hasUpdated
                    )
                )
            )
        }

        suspend fun withPersistConversationsSuccess() = apply {
            everySuspend {
                persistConversations(any(), eq(listOf(CONVERSATION_RESPONSE)),  eq(true), any())
            } returns Either.Right(Unit)
        }

        fun arrange(): Pair<Arrangement, UpdateConversationProtocolUseCase> =
            this to UpdateConversationProtocolUseCaseImpl(conversationRepository, persistConversations)
    }

    private companion object {
        const val GROUP_ID = "group-id"
        const val EPOCH = 5UL
        val MIXED_RESPONSE = CONVERSATION_RESPONSE.copy(groupId = GROUP_ID, epoch = EPOCH, protocol = ConvProtocol.MIXED)
    }
}
