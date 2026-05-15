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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteConversationUseCaseTest {

    @Test
    fun givenMlsConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocallyAndWiped() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withSuccessfulLeaveGroup(GROUP_ID)
                withDeletingConversationLocallySucceeding()
            }

        val result = useCase(arrangement.transactionContext, CONVERSATION_ID)

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsConversationRepository.leaveGroup(any(), eq(GROUP_ID)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }
    }

    @Test
    fun givenMlsConversation_WhenDeletingConversationLocallyFails_ThenShouldNotWipeAndReturnError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withDeletingConversationLocallyFailing()
            }

        val result = useCase(arrangement.transactionContext, CONVERSATION_ID)

        result.shouldFail()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }
        verifySuspend(VerifyMode.not) { arrangement.mlsConversationRepository.leaveGroup(any(), eq(GROUP_ID)) }
    }

    @Test
    fun givenMlsConversation_WhenWipingFails_ThenShouldReturnError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withGetConversationProtocolInfo(Either.Right(MLS_PROTOCOL_INFO))
                withDeletingConversationLocallySucceeding()
                withFailedLeaveGroup(GROUP_ID)
            }

        val result = useCase(arrangement.transactionContext, CONVERSATION_ID)

        result.shouldFail()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.deleteConversationLocally(eq(CONVERSATION_ID)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsConversationRepository.leaveGroup(any(), eq(GROUP_ID)) }
    }

    private class Arrangement {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val transactionContext = mock<CryptoTransactionContext>(mode = MockMode.autoUnit)
        private val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)

        val persistenceEventHookNotifier: PersistenceEventHookNotifier = object : PersistenceEventHookNotifier {}

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteConversationUseCase> = run {
            every { transactionContext.mls } returns mlsContext
            val useCase = DeleteConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                persistenceEventHookNotifier = persistenceEventHookNotifier,
                selfUserId = SELF_USER_ID,
            )
            block()
            return this to useCase
        }

        suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns result
        }

        suspend fun withDeletingConversationLocallySucceeding() {
            everySuspend { conversationRepository.deleteConversationLocally(any()) } returns Either.Right(true)
        }

        suspend fun withDeletingConversationLocallyFailing() {
            everySuspend {
                conversationRepository.deleteConversationLocally(any())
            } returns Either.Left(CoreFailure.Unknown(RuntimeException("some error")))
        }

        suspend fun withSuccessfulLeaveGroup(groupId: GroupID) {
            everySuspend { mlsConversationRepository.leaveGroup(any(), eq(groupId)) } returns Either.Right(Unit)
        }

        suspend fun withFailedLeaveGroup(groupId: GroupID) {
            everySuspend { mlsConversationRepository.leaveGroup(any(), eq(groupId)) } returns Either.Left(CoreFailure.Unknown(null))
        }
    }

    companion object {
        val SELF_USER_ID = UserId("self-user", "self-domain")
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
