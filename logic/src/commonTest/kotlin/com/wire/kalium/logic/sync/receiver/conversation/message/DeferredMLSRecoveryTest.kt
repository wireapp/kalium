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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeferredMLSRecoveryTest {

    @Test
    fun givenRecoveryRequestedDuringReceiving_whenSyncGoesLive_thenEachRequestRunsOnce() = runTest {
        val crypto = CryptoTransactionProviderArrangementMokkeryImpl().apply { withTransactionReturning(Either.Right(Unit)) }
        val live = CompletableDeferred<Unit>()
        val syncStateObserver = mock<SyncStateObserver> { everySuspend { waitUntilLive() } calls { live.await() } }
        val verifier = mock<StaleEpochVerifier> { everySuspend { verifyEpoch(any(), any(), any(), any()) } returns Either.Right(Unit) }
        val reset = mock<ResetMLSConversationUseCase> { everySuspend { invoke(any(), any()) } returns ResetMLSConversationResult.Success }
        val recovery = DeferredMLSRecovery(syncStateObserver, crypto.cryptoTransactionProvider, verifier, reset)
        backgroundScope.launch { recovery.run() }

        repeat(2) { recovery.staleEpochVerifier.verifyEpoch(crypto.transactionContext, CONVERSATION_ID, null, null) }
        recovery.resetMLSConversation(CONVERSATION_ID, crypto.transactionContext)
        runCurrent()
        verifySuspend(VerifyMode.not) { verifier.verifyEpoch(any(), any(), any(), any()) }

        live.complete(Unit)
        runCurrent()
        verifySuspend(VerifyMode.exactly(1)) { verifier.verifyEpoch(any(), CONVERSATION_ID, null, null) }
        verifySuspend(VerifyMode.exactly(1)) { reset(CONVERSATION_ID, any()) }
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.ID
    }
}
