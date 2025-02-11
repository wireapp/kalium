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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationMutedStatusUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    private lateinit var updateConversationMutedStatus: UpdateConversationMutedStatusUseCase

    @BeforeTest
    fun setup() {
        updateConversationMutedStatus = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenAConversationId_whenInvokingAMutedStatusChange_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        coEvery {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }.returns(Either.Right(Unit))

        coEvery {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        }.returns(Either.Right(Unit))

        val result = updateConversationMutedStatus(conversationId, MutedConversationStatus.AllMuted)
        assertEquals(ConversationUpdateStatusResult.Success::class, result::class)

        coVerify {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }.wasInvoked(exactly = once)

        coVerify {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationId_whenInvokingAMutedStatusChangeAndFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID

        coEvery {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }.returns(Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException("some error"))))

        val result = updateConversationMutedStatus(conversationId, MutedConversationStatus.AllMuted)
        assertEquals(ConversationUpdateStatusResult.Failure::class, result::class)

        coVerify {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }.wasInvoked(exactly = once)

        coVerify {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        }.wasNotInvoked()

    }

}
