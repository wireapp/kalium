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

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetConversationEpochUseCaseTest {

    @Test
    fun givenGroupID_whenInvoke_thenSuccessReturnedWithEpoch() = runTest {
        val expectedEpoch = 75UL
        val (arrangement, useCase) = Arrangement()
            .arrange {
                dispatcher = this@runTest.testKaliumDispatcher
                withMLSTransactionReturning<ULong>(Either.Right(expectedEpoch))
                withConversationEpoch(expectedEpoch)
            }

        val result = useCase(TEST_GROUP_ID)

        assertIs<GetConversationEpochUseCase.Result.Success>(result)
        assertEquals(expectedEpoch, result.epoch)
    }

    @Test
    fun givenGroupIDAndMLSContextThrowsException_whenInvoke_thenFailureReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                dispatcher = this@runTest.testKaliumDispatcher
                withMLSTransactionReturning<ULong>(Either.Right(0UL))
                withConversationEpochException()
            }

        val result = useCase(TEST_GROUP_ID)

        assertIs<GetConversationEpochUseCase.Result.Failure>(result)
        assertIs<MLSFailure.Generic>(result.coreFailure)
    }

    private companion object {
        val TEST_GROUP_ID = GroupID("group-id")
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        var dispatcher: KaliumDispatcher = TestKaliumDispatcher
        private lateinit var getConversationEpochUseCase: GetConversationEpochUseCase

        suspend fun withConversationEpoch(epoch: ULong) = apply {
            coEvery {
                mlsContext.conversationEpoch(any())
            } returns epoch
        }

        suspend fun withConversationEpochException() = apply {
            coEvery {
                mlsContext.conversationEpoch(any())
            } throws IllegalStateException("MLS error")
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetConversationEpochUseCase> {
            block()
            getConversationEpochUseCase = GetConversationEpochUseCaseImpl(
                cryptoTransactionProvider,
                dispatcher
            )

            return this to getConversationEpochUseCase
        }
    }
}
