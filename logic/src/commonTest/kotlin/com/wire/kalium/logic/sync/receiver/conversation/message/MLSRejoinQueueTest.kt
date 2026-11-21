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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
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

class MLSRejoinQueueTest {

    @Test
    fun givenQueuedGroups_whenRejoining_thenEachIsRejoinedOnceInItsOwnTransaction() = runTest {
        val arrangement = Arrangement().withVerifyEpoch(Either.Right(Unit))

        arrangement.queue.request(FIRST)
        arrangement.queue.request(FIRST)
        arrangement.queue.request(SECOND)
        arrangement.queue.rejoinQueued()
        arrangement.queue.rejoinQueued()

        verifySuspend(VerifyMode.exactly(2)) { arrangement.cryptoTransactionProvider.transaction<Unit>("rejoinStaleMLSGroup", any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), SECOND, null, any()) }
    }

    @Test
    fun givenRejoinFails_whenRejoining_thenItIsForgottenUntilRequestedAgain() = runTest {
        val arrangement = Arrangement().withVerifyEpoch(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        arrangement.queue.request(FIRST)
        arrangement.queue.rejoinQueued()
        arrangement.queue.rejoinQueued()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }

        arrangement.queue.request(FIRST)
        arrangement.queue.rejoinQueued()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val staleEpochVerifier = mock<StaleEpochVerifier>()
        val queue = MLSRejoinQueueImpl(cryptoTransactionProvider, staleEpochVerifier)

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            withTransactionReturning(result)
            everySuspend { staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) } returns result
        }
    }

    private companion object {
        val FIRST = ConversationId("first", "domain")
        val SECOND = ConversationId("second", "domain")
    }
}
