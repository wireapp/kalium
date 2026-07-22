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
package com.wire.kalium.logic.feature.proteus

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
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

class GenerateAndUploadNewPrekeysUseCaseTest {

    @Test
    fun givenNonPositiveCount_whenInvoking_thenShouldSucceedWithoutTouchingRepository() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(0).shouldSucceed()

        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.mostRecentPreKeyId() }
        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.generateNewPreKeys(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.uploadNewPrekeyBatch(any()) }
    }

    @Test
    fun givenGeneratingWontOverflow_whenInvoking_thenShouldGenerateFromMostRecentKeyPlusOne() = runTest {
        val mostRecentKey = 50
        val count = 10
        val (arrangement, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Right(mostRecentKey))
            withGenerateNewPreKeysReturning(Either.Right(listOf(PreKeyCrypto(mostRecentKey + count, ""))))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Right(Unit))
        }.arrange(maxPreKeyId = 1000)

        useCase(count).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(eq(mostRecentKey + 1), eq(count))
        }
    }

    @Test
    fun givenGeneratingWouldOverflow_whenInvoking_thenShouldGenerateFromZero() = runTest {
        val maxPreKeyId = 100
        val count = 10
        val (arrangement, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Right(maxPreKeyId - 5))
            withGenerateNewPreKeysReturning(Either.Right(listOf(PreKeyCrypto(count, ""))))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Right(Unit))
        }.arrange(maxPreKeyId = maxPreKeyId)

        useCase(count).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.generateNewPreKeys(eq(0), eq(count))
        }
    }

    @Test
    fun givenMostRecentPreKeyIdFails_whenInvoking_thenShouldPropagateFailureWithoutGenerating() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Left(failure))
        }.arrange()

        useCase(10).shouldFail { assertEquals(failure, it) }

        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.generateNewPreKeys(any(), any()) }
    }

    @Test
    fun givenGeneratingFails_whenInvoking_thenShouldPropagateFailureWithoutUploadingOrUpdating() = runTest {
        val failure = CoreFailure.NotSupportedByProteus
        val (arrangement, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Right(50))
            withGenerateNewPreKeysReturning(Either.Left(failure))
        }.arrange()

        useCase(10).shouldFail { assertEquals(failure, it) }

        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.updateMostRecentPreKeyId(any()) }
        verifySuspend(VerifyMode.not) { arrangement.preKeyRepository.uploadNewPrekeyBatch(any()) }
    }

    @Test
    fun givenUploadingFails_whenInvoking_thenShouldPropagateFailure() = runTest {
        val failure = CoreFailure.NotSupportedByProteus
        val (_, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Right(50))
            withGenerateNewPreKeysReturning(Either.Right(listOf(PreKeyCrypto(51, ""))))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Left(failure))
        }.arrange()

        useCase(10).shouldFail { assertEquals(failure, it) }
    }

    @Test
    fun givenEverythingSucceeds_whenInvoking_thenShouldUpdateMostRecentKeyAndUploadBatch() = runTest {
        val generatedPreKeys = listOf(PreKeyCrypto(51, ""), PreKeyCrypto(60, ""))
        val (arrangement, useCase) = Arrangement().apply {
            withMostRecentPreKeyId(Either.Right(50))
            withGenerateNewPreKeysReturning(Either.Right(generatedPreKeys))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Right(Unit))
        }.arrange()

        useCase(10).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.updateMostRecentPreKeyId(eq(generatedPreKeys.last().id))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.preKeyRepository.uploadNewPrekeyBatch(eq(generatedPreKeys))
        }
    }

    private class Arrangement {
        val preKeyRepository: PreKeyRepository = mock(mode = MockMode.autoUnit)

        suspend fun withMostRecentPreKeyId(result: Either<StorageFailure, Int>) = apply {
            everySuspend { preKeyRepository.mostRecentPreKeyId() } returns result
        }

        suspend fun withGenerateNewPreKeysReturning(result: Either<CoreFailure, List<PreKeyCrypto>>) = apply {
            everySuspend { preKeyRepository.generateNewPreKeys(any(), any()) } returns result
        }

        suspend fun withUpdatingMostRecentPrekeyReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { preKeyRepository.updateMostRecentPreKeyId(any()) } returns result
        }

        suspend fun withUploadNewPrekeyBatchReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { preKeyRepository.uploadNewPrekeyBatch(any()) } returns result
        }

        fun arrange(maxPreKeyId: Int = ProteusPreKeyRefiller.MAX_PREKEY_ID) =
            this to GenerateAndUploadNewPrekeysUseCaseImpl(preKeyRepository, maxPreKeyId)
    }
}
