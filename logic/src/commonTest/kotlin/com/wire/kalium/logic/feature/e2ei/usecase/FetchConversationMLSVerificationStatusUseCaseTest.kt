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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestTeam
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FetchConversationMLSVerificationStatusUseCaseTest {

    @Test
    fun givenErrorOnGettingConversation_whenCalledToCheck_thenFetchingMLSVerificationIsNotCalled() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        useCase(MockConversation.ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) { arrangement.fetchMLSVerificationStatusUseCase(any()) }
    }

    @Test
    fun givenProteusConversation_whenCalledToCheck_thenFetchingMLSVerificationIsNotCalled() = runTest {
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Right(MockConversation.oneOnOne(teamId = TestTeam.TEAM_ID)))
        }

        useCase(MockConversation.ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) { arrangement.fetchMLSVerificationStatusUseCase(any()) }
    }

    @Test
    fun givenMLSConversation_whenCalledToCheck_thenFetchingMLSVerificationIsCalled() = runTest {
        val protocolInfo = MockConversation.MLS_PROTOCOL_INFO
        val (arrangement, useCase) = arrange {
            withConversationDetailsByIdReturning(Either.Right(MockConversation.oneOnOne(
                teamId = TestTeam.TEAM_ID,
                protocolInfo = protocolInfo
            )))
        }

        useCase(MockConversation.ID)
        advanceUntilIdle()

        verifySuspend { arrangement.fetchMLSVerificationStatusUseCase(protocolInfo.groupId) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val fetchMLSVerificationStatusUseCase = mock<FetchMLSVerificationStatusUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange() = let {
            block()
            this to FetchConversationMLSVerificationStatusUseCaseImpl(
                conversationRepository = conversationRepository,
                fetchMLSVerificationStatusUseCase = fetchMLSVerificationStatusUseCase
            )
        }

        suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>) {
            everySuspend { conversationRepository.getConversationById(any()) } returns result
        }
    }
}
