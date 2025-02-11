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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetConversationProtocolInfoUseCaseTest {

    @Test
    fun givenGetConversationProtocolFails_whenInvoke_thenFailureReturned() = runTest {
        val (_, useCase) = Arrangement()
            .arrange {
                dispatcher = this@runTest.testKaliumDispatcher
                withConversationProtocolInfo(Either.Left(StorageFailure.DataNotFound))
            }

        assertTrue(useCase(ConversationId("ss", "dd")) is GetConversationProtocolInfoUseCase.Result.Failure)
    }

    @Test
    fun givenGetConversationProtocolSucceed_whenInvoke_thenSuccessReturned() = runTest {
        val (_, useCase) = Arrangement()
            .arrange {
                dispatcher = this@runTest.testKaliumDispatcher
                withConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
            }

        val result = useCase(ConversationId("ss", "dd"))

        assertTrue(result is GetConversationProtocolInfoUseCase.Result.Success)
        assertEquals(Conversation.ProtocolInfo.Proteus, result.protocolInfo)
    }

    private class Arrangement : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        var dispatcher: KaliumDispatcher = TestKaliumDispatcher
        private lateinit var getConversationProtocolInfo: GetConversationProtocolInfoUseCase

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetConversationProtocolInfoUseCase> {
            block()
            getConversationProtocolInfo = GetConversationProtocolInfoUseCase(
                conversationRepository,
                dispatcher
            )

            return this to getConversationProtocolInfo
        }
    }
}
