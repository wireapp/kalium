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
package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallModerationAction
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveCallModerationActionsUseCaseTest {
    @Test
    fun givenOngoingCallReceivingModerationActions_whenObservingModerationActions_thenActionsAreEmitterProperly() = runTest {
        val action1 = CallModerationAction("0", TestUser.USER_ID, CallModerationAction.Type.MUTED)
        val action2 = CallModerationAction("1", TestUser.OTHER_USER_ID, CallModerationAction.Type.MUTED)
        val actionsFlow = MutableStateFlow(action1)
        val (_, useCase) = Arrangement()
            .withObserveActionsReturning(actionsFlow)
            .arrange()

        useCase(ConversationId("conversation-id", "domain")).test {
            assertEquals(action1, awaitItem())

            actionsFlow.emit(action2)
            assertEquals(action2, awaitItem())
        }
    }

    inner class Arrangement {
        internal val callModerationActionsRepository = mock<CallModerationActionsRepository>()

        fun withObserveActionsReturning(actionsFlow: Flow<CallModerationAction>) = apply {
            every {
                callModerationActionsRepository.observeActions(any())
            } returns actionsFlow
        }
        internal fun arrange() = this to ObserveCallModerationActionsUseCaseImpl(callModerationActionsRepository)
    }
}
