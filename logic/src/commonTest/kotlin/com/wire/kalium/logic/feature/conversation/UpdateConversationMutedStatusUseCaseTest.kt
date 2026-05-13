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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationMutedStatusUseCaseTest {

    private val conversationRepository: ConversationRepository = mock()

    private lateinit var updateConversationMutedStatus: UpdateConversationMutedStatusUseCase

    @BeforeTest
    fun setup() {
        updateConversationMutedStatus = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenAConversationId_whenInvokingAMutedStatusChange_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        everySuspend {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        } returns Either.Right(Unit)

        everySuspend {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        } returns Either.Right(Unit)

        val result = updateConversationMutedStatus(conversationId, MutedConversationStatus.AllMuted)
        assertEquals(ConversationUpdateStatusResult.Success::class, result::class)

        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        }
    }

    @Test
    fun givenAConversationId_whenInvokingAMutedStatusChangeAndFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID

        everySuspend {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        } returns Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException("some error")))

        val result = updateConversationMutedStatus(conversationId, MutedConversationStatus.AllMuted)
        assertEquals(ConversationUpdateStatusResult.Failure::class, result::class)

        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.updateMutedStatusRemotely(any(), eq(MutedConversationStatus.AllMuted), any())
        }

        verifySuspend(VerifyMode.not) {
            conversationRepository.updateMutedStatusLocally(any(), eq(MutedConversationStatus.AllMuted), any())
        }

    }

}
