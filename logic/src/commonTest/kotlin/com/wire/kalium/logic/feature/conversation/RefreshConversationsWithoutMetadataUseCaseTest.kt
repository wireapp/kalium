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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class RefreshConversationsWithoutMetadataUseCaseTest {

    @Test
    fun givenConversationsWithoutMetadata_whenRefreshing_thenShouldRefreshThoseConversationInformation() = runTest {
        val (arrangement, refreshConversationsWithoutMetadata) = Arrangement(testKaliumDispatcher)
            .withResponse()
            .arrange()

        refreshConversationsWithoutMetadata()

        coVerify {
            arrangement.conversationRepository.syncConversationsWithoutMetadata()
        }.wasInvoked(once)
    }

    private class Arrangement(var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
                val conversationRepository = mock(ConversationRepository::class)

        suspend fun withResponse(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            coEvery {
                conversationRepository.syncConversationsWithoutMetadata()
            }.returns(result)
        }

        fun arrange() = this to RefreshConversationsWithoutMetadataUseCaseImpl(
            conversationRepository,
            dispatcher
        )
    }
}
