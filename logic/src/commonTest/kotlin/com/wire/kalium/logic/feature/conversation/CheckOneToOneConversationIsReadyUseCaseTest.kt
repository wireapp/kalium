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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckOneToOneConversationIsReadyUseCaseTest {

    @Test
    fun givenNoActiveConversation_whenCheckingReadiness_thenReturnNotReady() = runTest {
        val (_, useCase) = arrange {
            withActiveConversation(Either.Left(StorageFailure.DataNotFound))
        }

        assertEquals(CheckOneToOneConversationIsReadyUseCase.Result.NotReady, useCase(OTHER_USER_ID))
    }

    @Test
    fun givenStorageFailure_whenCheckingReadiness_thenReturnFailure() = runTest {
        val failure = StorageFailure.Generic(IllegalStateException("failure"))
        val (_, useCase) = arrange {
            withActiveConversation(Either.Left(failure))
        }

        val result = useCase(OTHER_USER_ID)

        assertIs<CheckOneToOneConversationIsReadyUseCase.Result.Failure>(result)
        assertEquals(failure, result.coreFailure)
    }

    @Test
    fun givenActiveProteusConversation_whenCheckingReadiness_thenReturnReadyWithoutCheckingCoreCrypto() = runTest {
        val (arrangement, useCase) = arrange {
            withActiveConversation(Either.Right(PROTEUS_CONVERSATION))
        }

        val result = useCase(OTHER_USER_ID)

        assertIs<CheckOneToOneConversationIsReadyUseCase.Result.Ready>(result)
        assertEquals(PROTEUS_CONVERSATION, result.conversation)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
        }
    }

    @Test
    fun givenActiveMLSConversationWithEstablishedGroup_whenCheckingReadiness_thenReturnReady() = runTest {
        val (_, useCase) = arrange {
            withActiveConversation(Either.Right(MLS_CONVERSATION))
            withEstablishedMLSGroup(Either.Right(true))
        }

        val result = useCase(OTHER_USER_ID)

        assertIs<CheckOneToOneConversationIsReadyUseCase.Result.Ready>(result)
        assertEquals(MLS_CONVERSATION, result.conversation)
    }

    @Test
    fun givenActiveMLSConversationWithoutEstablishedGroup_whenCheckingReadiness_thenReturnNotReady() = runTest {
        val (_, useCase) = arrange {
            withActiveConversation(Either.Right(MLS_CONVERSATION))
            withEstablishedMLSGroup(Either.Right(false))
        }

        assertEquals(CheckOneToOneConversationIsReadyUseCase.Result.NotReady, useCase(OTHER_USER_ID))
    }

    @Test
    fun givenCoreCryptoFailure_whenCheckingReadiness_thenReturnFailure() = runTest {
        val (_, useCase) = arrange {
            withActiveConversation(Either.Right(MLS_CONVERSATION))
            withEstablishedMLSGroup(Either.Left(MLSFailure.Disabled))
        }

        val result = useCase(OTHER_USER_ID)

        assertIs<CheckOneToOneConversationIsReadyUseCase.Result.Failure>(result)
        assertEquals(MLSFailure.Disabled, result.coreFailure)
    }

    @Test
    fun givenActiveMixedConversation_whenCheckingReadiness_thenReturnNotReadyWithoutCheckingCoreCrypto() = runTest {
        val (arrangement, useCase) = arrange {
            withActiveConversation(Either.Right(MIXED_CONVERSATION))
        }

        assertEquals(CheckOneToOneConversationIsReadyUseCase.Result.NotReady, useCase(OTHER_USER_ID))
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement().apply {
        withMLSTransactionReturning(Either.Right(Unit))
        block()
    }.let { arrangement ->
        arrangement to CheckOneToOneConversationIsReadyUseCaseImpl(
            conversationRepository = arrangement.conversationRepository,
            mlsConversationRepository = arrangement.mlsConversationRepository,
            transactionProvider = arrangement.cryptoTransactionProvider,
        )
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)

        suspend fun withActiveConversation(result: Either<StorageFailure, Conversation>) = apply {
            everySuspend {
                conversationRepository.observeOneToOneConversationWithOtherUser(any())
            } returns flowOf(result)
        }

        suspend fun withEstablishedMLSGroup(result: Either<MLSFailure, Boolean>) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
            } returns result
        }
    }

    private companion object {
        val OTHER_USER_ID = TestUser.OTHER_USER_ID
        val PROTEUS_CONVERSATION = TestConversation.ONE_ON_ONE()
        val MLS_CONVERSATION = TestConversation.ONE_ON_ONE(TestConversation.MLS_PROTOCOL_INFO)
        val MIXED_CONVERSATION = TestConversation.ONE_ON_ONE(TestConversation.MIXED_PROTOCOL_INFO)
    }
}
