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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.GroupWithEpoch
import com.wire.kalium.logic.framework.TestConversation
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

        verifySuspend { arrangement.fetchMLSVerificationStatusUseCase(eq(TestConversation.GROUP_ID)) }
    }

    private suspend fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) {
        val epochChangesObserver = mock<EpochChangesObserver>(mode = MockMode.autoUnit)
        val fetchMLSVerificationStatusUseCase = mock<FetchMLSVerificationStatusUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange() = let {
            block()
            this to ObserveE2EIConversationsVerificationStatusesUseCaseImpl(
                epochChangesObserver = epochChangesObserver,
                fetchMLSVerificationStatus = fetchMLSVerificationStatusUseCase,
                kaliumLogger = kaliumLogger,
            )
        }

        fun withObserveEpochChanges(flow: Flow<GroupWithEpoch>) {
            every { epochChangesObserver.observe() } returns flow
        }
    }
}
