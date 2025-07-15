/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteConversationUseCaseTest {

    @Test
    fun givenMlsConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocallyAndWiped() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withSuccessfulLeaveGroup(GROUP_ID)
                withDeletingConversationLocallySucceeding()
            }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        result.shouldSucceed()
        coVerify { arrangement.mlsConversationRepository.leaveGroup(eq(GROUP_ID)) }.wasInvoked(once)
        coVerify { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }.wasInvoked(once)
    }

    @Test
    fun givenMlsConversation_WhenDeletingConversationLocallyFails_ThenShouldNotWipeAndReturnError() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withDeletingConversationLocallyFailing()
            }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        result.shouldFail()
        coVerify { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }.wasInvoked(once)
        coVerify { arrangement.mlsConversationRepository.leaveGroup(eq(GROUP_ID)) }.wasNotInvoked()
    }

    @Test
    fun givenMlsConversation_WhenWipingFails_ThenShouldReturnError() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withDeletingConversationLocallySucceeding()
                withFailedLeaveGroup(GROUP_ID)
            }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        result.shouldFail()
        coVerify { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }.wasInvoked(once)
        coVerify { arrangement.mlsConversationRepository.leaveGroup(eq(GROUP_ID)) }.wasInvoked(once)
    }

    private class Arrangement :
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl() {

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteConversationUseCase> = run {
            val useCase = DeleteConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository
            )
            block()
            return this to useCase
        }
    }

    companion object {
        val GROUP_ID = GroupID("mls_group_id")
        val CONVERSATION_ID = ConversationId("conv_id", "conv_domain")

        val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            GROUP_ID,
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            epoch = 1UL,
            keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
    }
}
