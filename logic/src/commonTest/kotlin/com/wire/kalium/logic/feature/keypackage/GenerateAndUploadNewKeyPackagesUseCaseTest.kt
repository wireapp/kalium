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
package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateAndUploadNewKeyPackagesUseCaseTest {

    @Test
    fun givenNonPositiveCount_whenInvoking_thenShouldSucceedWithoutTouchingRepository() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(arrangement.mlsContext, 0).shouldSucceed()

        verifySuspend(VerifyMode.not) { arrangement.currentClientIdProvider() }
        verifySuspend(VerifyMode.not) { arrangement.keyPackageRepository.uploadNewKeyPackages(any(), any(), any()) }
    }

    @Test
    fun givenNoCurrentClientId_whenInvoking_thenShouldPropagateFailureWithoutUploading() = runTest {
        val failure = CoreFailure.MissingClientRegistration
        val (arrangement, useCase) = Arrangement().apply {
            withCurrentClientIdReturning(Either.Left(failure))
        }.arrange()

        useCase(arrangement.mlsContext, 10).shouldFail { assertEquals(failure, it) }

        verifySuspend(VerifyMode.not) { arrangement.keyPackageRepository.uploadNewKeyPackages(any(), any(), any()) }
    }

    @Test
    fun givenUploadingFails_whenInvoking_thenShouldPropagateFailureAndNotNotifyCryptoStateChanged() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement().apply {
            withCurrentClientIdReturning(Either.Right(TestClient.CLIENT_ID))
            withUploadNewKeyPackagesReturning(Either.Left(failure))
        }.arrange()

        useCase(arrangement.mlsContext, 10).shouldFail { assertEquals(failure, it) }

        verifySuspend(VerifyMode.not) { arrangement.cryptoStateChangeHookNotifier.onCryptoStateChanged(any()) }
    }

    @Test
    fun givenEverythingSucceeds_whenInvoking_thenShouldUploadRequestedAmountAndNotifyCryptoStateChanged() = runTest {
        val count = 42
        val (arrangement, useCase) = Arrangement().apply {
            withCurrentClientIdReturning(Either.Right(TestClient.CLIENT_ID))
            withUploadNewKeyPackagesReturning(Either.Right(Unit))
        }.arrange()

        useCase(arrangement.mlsContext, count).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(eq(arrangement.mlsContext), eq(TestClient.CLIENT_ID), eq(count))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoStateChangeHookNotifier.onCryptoStateChanged(eq(TestUser.SELF.id))
        }
    }

    private class Arrangement {
        val keyPackageRepository: KeyPackageRepository = mock()
        val currentClientIdProvider: CurrentClientIdProvider = mock()
        val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier = mock(mode = MockMode.autoUnit)
        val mlsContext: MlsCoreCryptoContext = mock(mode = MockMode.autoUnit)

        suspend fun withCurrentClientIdReturning(result: Either<CoreFailure, com.wire.kalium.logic.data.conversation.ClientId>) = apply {
            everySuspend { currentClientIdProvider() } returns result
        }

        suspend fun withUploadNewKeyPackagesReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { keyPackageRepository.uploadNewKeyPackages(any(), any(), any()) } returns result
        }

        fun arrange() = this to GenerateAndUploadNewKeyPackagesUseCaseImpl(
            keyPackageRepository = keyPackageRepository,
            currentClientIdProvider = currentClientIdProvider,
            selfUserId = TestUser.SELF.id,
            cryptoStateChangeHookNotifier = cryptoStateChangeHookNotifier,
        )
    }
}
