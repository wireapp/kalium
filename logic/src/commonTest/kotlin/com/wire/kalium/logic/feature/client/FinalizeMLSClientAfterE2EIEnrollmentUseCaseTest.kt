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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FinalizeMLSClientAfterE2EIEnrollmentUseCaseTest {

    @Test
    fun givenE2EIEnrollmentFinished_whenFinalizing_thenRegisterMLSClientBeforeClearingBlockAndForcingSlowSync() = runTest {
        val clientId = ClientId("client-id")
        val calls = mutableListOf<String>()
        val (arrangement, useCase) = arrange {
            withCurrentClientId(clientId)
            withRegisterMLSClient(clientId, calls, Either.Right(RegisterMLSClientResult.Success))
            withClearClientRegistrationBlockedByE2EI(calls)
            withClearLastSlowSyncCompletionInstant(calls)
        }

        useCase.invoke()

        assertEquals(
            listOf(
                REGISTER_MLS_CLIENT,
                CLEAR_E2EI_BLOCK,
                CLEAR_LAST_SLOW_SYNC_COMPLETION
            ),
            calls
        )
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.registerMLSClient(clientId)
            arrangement.clientRepository.clearClientRegistrationBlockedByE2EI()
            arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
        }
    }

    @Test
    fun givenMLSClientRegistrationFails_whenFinalizing_thenDoNotUnblockClientRegistration() = runTest {
        val clientId = ClientId("client-id")
        val (arrangement, useCase) = arrange {
            withCurrentClientId(clientId)
            withRegisterMLSClient(clientId, mutableListOf(), Either.Left(CoreFailure.Unknown(null)))
        }

        useCase.invoke()

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.clearClientRegistrationBlockedByE2EI()
            arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
        }
    }

    private fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, FinalizeMLSClientAfterE2EIEnrollmentUseCase> =
        Arrangement().apply(block).arrange()

    private class Arrangement {
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val registerMLSClient = mock<RegisterMLSClientUseCase>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        private var currentClientIdProvider = CurrentClientIdProvider { Either.Right(ClientId("client-id")) }

        fun withCurrentClientId(clientId: ClientId) = apply {
            currentClientIdProvider = CurrentClientIdProvider { Either.Right(clientId) }
        }

        fun withRegisterMLSClient(
            clientId: ClientId,
            calls: MutableList<String>,
            result: Either<CoreFailure, RegisterMLSClientResult>
        ) = apply {
            everySuspend { registerMLSClient(clientId) } calls {
                calls += REGISTER_MLS_CLIENT
                result
            }
        }

        fun withClearClientRegistrationBlockedByE2EI(calls: MutableList<String>) = apply {
            everySuspend { clientRepository.clearClientRegistrationBlockedByE2EI() } calls {
                calls += CLEAR_E2EI_BLOCK
                Either.Right(Unit)
            }
        }

        fun withClearLastSlowSyncCompletionInstant(calls: MutableList<String>) = apply {
            everySuspend { slowSyncRepository.clearLastSlowSyncCompletionInstant() } calls {
                calls += CLEAR_LAST_SLOW_SYNC_COMPLETION
            }
        }

        fun arrange() = this to FinalizeMLSClientAfterE2EIEnrollmentUseCaseImpl(
            clientRepository = clientRepository,
            currentClientIdProvider = currentClientIdProvider,
            registerMLSClient = registerMLSClient,
            slowSyncRepository = slowSyncRepository
        )
    }

    private companion object {
        const val REGISTER_MLS_CLIENT = "registerMLSClient"
        const val CLEAR_E2EI_BLOCK = "clearE2EIBlock"
        const val CLEAR_LAST_SLOW_SYNC_COMPLETION = "clearLastSlowSyncCompletion"
    }
}
