/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetConversationEpochFromCCUseCaseTest {

    @Test
    fun givenMLSConversation_whenInvoked_thenReturnEpochFromCC() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversation(TestConversation.GROUP(TestConversation.MLS_PROTOCOL_INFO).right())
            .withCCEpoch(EXPECTED_EPOCH)
            .arrange()

        val result = useCase(TestConversation.ID)

        coVerify {
            arrangement.mlsContext.conversationEpoch(eq(TestConversation.GROUP_ID.value))
        }.wasInvoked(exactly = 1)
        assertEquals(GetConversationEpochFromCCResult.Success(EXPECTED_EPOCH), result)
    }

    @Test
    fun givenProteusConversation_whenInvoked_thenReturnNotMlsConversationFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversation(TestConversation.GROUP(Conversation.ProtocolInfo.Proteus).right())
            .arrange()

        val result = useCase(TestConversation.ID)

        coVerify {
            arrangement.cryptoTransactionProvider.mlsTransaction<ULong>(any(), any())
        }.wasNotInvoked()
        assertIs<GetConversationEpochFromCCResult.Failure.NotMlsConversation>(result)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {
        val conversationRepository = mock(ConversationRepository::class)
        private var ccEpoch = EXPECTED_EPOCH

        suspend fun withConversation(result: Either<StorageFailure, Conversation>) = apply {
            coEvery { conversationRepository.getConversationById(any()) } returns result
        }

        suspend fun withCCEpoch(epoch: ULong) = apply {
            ccEpoch = epoch
            coEvery { mlsContext.conversationEpoch(any()) } returns epoch
        }

        suspend fun arrange(): Pair<Arrangement, GetConversationEpochFromCCUseCaseImpl> {
            withMLSTransactionReturning(ccEpoch.right())
            return this to GetConversationEpochFromCCUseCaseImpl(
                conversationRepository = conversationRepository,
                transactionProvider = cryptoTransactionProvider,
            )
        }
    }

    private companion object {
        val EXPECTED_EPOCH = 42UL
    }
}
