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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleWrongExternalSenderUseCaseTest {

    @Test
    fun givenSelfDomainGroupWithWrongExternalSender_whenInvoking_thenResetAndReturnTrue() = runTest {
        val (arrangement, useCase) = Arrangement().withWrongExternalSender(true).arrange()

        assertTrue(useCase(arrangement.transactionContext, SELF_DOMAIN_CONVERSATION_ID))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.resetMLSConversation(SELF_DOMAIN_CONVERSATION_ID, arrangement.transactionContext)
        }
    }

    @Test
    fun givenResetFails_whenInvoking_thenReturnFalse() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withWrongExternalSender(true)
            .withResetResult(ResetMLSConversationResult.Failure(CoreFailure.Unknown(null)))
            .arrange()

        assertFalse(useCase(arrangement.transactionContext, SELF_DOMAIN_CONVERSATION_ID))
    }

    @Test
    fun givenResetDisabled_whenInvoking_thenReturnFalseWithoutReset() = runTest {
        val (arrangement, useCase) = Arrangement().withWrongExternalSender(true).withResetEnabled(false).arrange()

        assertFalse(useCase(arrangement.transactionContext, SELF_DOMAIN_CONVERSATION_ID))

        verifySuspend(VerifyMode.not) { arrangement.resetMLSConversation(any(), any()) }
    }

    @Test
    fun givenRemoteGroup_whenInvoking_thenReturnFalseWithoutReset() = runTest {
        val (arrangement, useCase) = Arrangement().withWrongExternalSender(true).arrange()

        assertFalse(useCase(arrangement.transactionContext, REMOTE_CONVERSATION_ID))

        verifySuspend(VerifyMode.not) { arrangement.resetMLSConversation(any(), any()) }
    }

    @Test
    fun givenGroupWithRightExternalSender_whenInvoking_thenReturnFalseWithoutReset() = runTest {
        val (arrangement, useCase) = Arrangement().withWrongExternalSender(false).arrange()

        assertFalse(useCase(arrangement.transactionContext, SELF_DOMAIN_CONVERSATION_ID))

        verifySuspend(VerifyMode.not) { arrangement.resetMLSConversation(any(), any()) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val userConfig = mock<UserConfigRepository>()
        val conversationRepository = mock<ConversationRepository>()
        val mlsConversationRepository = mock<MLSConversationRepository>()
        val resetMLSConversation = mock<ResetMLSConversationUseCase>()
        private var resetEnabled = true
        private var resetResult: ResetMLSConversationResult = ResetMLSConversationResult.Success

        suspend fun withWrongExternalSender(result: Boolean) = apply {
            everySuspend { mlsConversationRepository.hasWrongExternalSender(any(), any(), any()) } returns result.right()
        }

        fun withResetEnabled(enabled: Boolean) = apply { resetEnabled = enabled }

        fun withResetResult(result: ResetMLSConversationResult) = apply { resetResult = result }

        suspend fun arrange(): Pair<Arrangement, HandleWrongExternalSenderUseCase> {
            everySuspend { userConfig.isMlsConversationsResetEnabled() } returns resetEnabled
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns MockConversation.MLS_PROTOCOL_INFO.right()
            everySuspend { resetMLSConversation(any(), any()) } returns resetResult
            return this to HandleWrongExternalSenderUseCaseImpl(
                selfUserId = TestUser.SELF.id,
                userConfig = userConfig,
                kaliumConfigs = KaliumConfigs(isMlsResetEnabled = true),
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                resetMLSConversation = resetMLSConversation,
            )
        }
    }

    private companion object {
        val SELF_DOMAIN_CONVERSATION_ID = ConversationId("conversation", TestUser.SELF.id.domain)
        val REMOTE_CONVERSATION_ID = ConversationId("conversation", "other.domain")
    }
}
