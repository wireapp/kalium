/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.conversation.ConversationVerificationStatusResult
import com.wire.kalium.logic.feature.conversation.ObserveConversationProtocolInfoUseCase
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface CheckConversationVerificationStatusUseCaseArrangement {

    val checkConversationVerificationStatus: ObserveConversationProtocolInfoUseCase

    fun withCheckVerificationStatusResult(result: Flow<ConversationVerificationStatusResult>)
}

internal class CheckConversationVerificationStatusUseCaseArrangementImpl: CheckConversationVerificationStatusUseCaseArrangement {

    @Mock
    override val checkConversationVerificationStatus = mock(ObserveConversationProtocolInfoUseCase::class)

    override fun withCheckVerificationStatusResult(result: Flow<ConversationVerificationStatusResult>) {
        given(checkConversationVerificationStatus)
            .suspendFunction(checkConversationVerificationStatus::invoke)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

}
