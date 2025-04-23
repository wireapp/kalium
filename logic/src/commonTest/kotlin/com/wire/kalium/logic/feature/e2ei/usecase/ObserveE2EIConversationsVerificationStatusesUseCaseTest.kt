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

import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchMLSVerificationStatusArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchMLSVerificationStatusArrangementImpl
import io.mockative.coVerify
import io.mockative.eq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveE2EIConversationsVerificationStatusesUseCaseTest {

    @Test
    fun givenEpochChanged_thenFetchingMLSVerificationIsCalled() = runTest {
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_WITH_EPOCH))
        }

        handler()
        advanceUntilIdle()

        coVerify { arrangement.fetchMLSVerificationStatusUseCase(eq(TestConversation.GROUP_ID)) }
            .wasInvoked()
    }

    private suspend fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : FetchMLSVerificationStatusArrangement by FetchMLSVerificationStatusArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl() {

        suspend fun arrange() = let {
            block()
            mockFetchMLSVerificationStatus()
            this to ObserveE2EIConversationsVerificationStatusesUseCaseImpl(
                epochChangesObserver = epochChangesObserver,
                fetchMLSVerificationStatus = fetchMLSVerificationStatusUseCase,
                kaliumLogger = kaliumLogger,
            )
        }
    }
}
