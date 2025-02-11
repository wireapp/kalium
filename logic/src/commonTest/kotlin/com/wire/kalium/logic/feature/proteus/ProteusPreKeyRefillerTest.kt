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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.PreKeyRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProteusPreKeyRefillerTest {

    @Test
    fun givenEnoughRemotePrekeys_whenRefilling_thenShouldNotDoGenerateNewPrekeys() = runTest {
        val (arrangement, proteusPreKeyRefiller) = arrange {
            lowOnPreKeysThreshold = 10
            remotePreKeyTargetCount = 100
            maxPreKeyId = 100

            // have 1 more in the backend
            val prekeys = Array(lowOnPreKeysThreshold + 1) { it }.toList()
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
        }

        proteusPreKeyRefiller.refillIfNeeded()

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.preKeyRepository.uploadNewPrekeyBatch(any())
        }.wasNotInvoked()

    }

    @Test
    fun givenPrekeysAreNeeded_andGeneratingWillCauseOverflow_thenShouldGenerateFromZero() = runTest {
        val remoteTarget = 100
        val (arrangement, proteusPreKeyRefiller) = arrange {
            // Last 10 possible prekeys before hitting limit
            withMostRecentPreKeyId(Either.Right(maxPreKeyId - 10))

            remotePreKeyTargetCount = remoteTarget

            lowOnPreKeysThreshold = 20
            val prekeys = fakePreKeys(1..10)
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Left(CoreFailure.NotSupportedByProteus))
        }

        proteusPreKeyRefiller.refillIfNeeded()

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(eq(0), eq(remoteTarget))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenPrekeysAreNeeded_andGeneratingWontCauseOverflow_thenShouldGenerateFromMostRecentKey() = runTest {
        val remoteTarget = 100
        val mostRecentKey = 50
        val (arrangement, proteusPreKeyRefiller) = arrange {
            withMostRecentPreKeyId(Either.Right(mostRecentKey))

            lowOnPreKeysThreshold = 20
            remotePreKeyTargetCount = remoteTarget
            maxPreKeyId = 1000

            val prekeys = fakePreKeys(1..10)
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Left(CoreFailure.NotSupportedByProteus))
        }

        proteusPreKeyRefiller.refillIfNeeded()

        coVerify {
            arrangement.preKeyRepository.generateNewPreKeys(eq(mostRecentKey + 1), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenGeneratingFails_thenShouldPropagateFailureAndDontPerformOtherActions() = runTest {
        val failure = CoreFailure.NotSupportedByProteus
        val (arrangement, proteusPreKeyRefiller) = arrange {
            withMostRecentPreKeyId(Either.Right(50))

            lowOnPreKeysThreshold = 20

            val prekeys = fakePreKeys(1..10)
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Left(failure))
        }

        proteusPreKeyRefiller.refillIfNeeded().shouldFail {
            assertEquals(failure, it)
        }

        coVerify {
            arrangement.preKeyRepository.uploadNewPrekeyBatch(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.preKeyRepository.updateMostRecentPreKeyId(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUploadingFails_thenShouldPropagateFailure() = runTest {
        val failure = CoreFailure.NotSupportedByProteus
        val (_, proteusPreKeyRefiller) = arrange {
            withMostRecentPreKeyId(Either.Right(50))
            lowOnPreKeysThreshold = 20

            val prekeys = fakePreKeys(1..10)
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Left(failure))
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Right(listOf(PreKeyCrypto(1, ""))))
        }

        proteusPreKeyRefiller.refillIfNeeded().shouldFail {
            assertEquals(failure, it)
        }
    }

    @Test
    fun givenGeneratingSucceeds_thenShouldSetMostRecentPreKeyAndUploadThem() = runTest {
        val mostRecentPreKeyId = 10
        val generatedPreKeys = listOf(
            PreKeyCrypto(2, "4"),
            PreKeyCrypto(5, "7"),
            PreKeyCrypto(mostRecentPreKeyId, "1")
        )
        val (arrangement, proteusPreKeyRefiller) = arrange {
            withMostRecentPreKeyId(Either.Right(50))

            lowOnPreKeysThreshold = 20

            val prekeys = fakePreKeys(1..10)
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Right(generatedPreKeys))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Right(Unit))
        }

        proteusPreKeyRefiller.refillIfNeeded()

        coVerify {
            arrangement.preKeyRepository.uploadNewPrekeyBatch(eq(generatedPreKeys))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.preKeyRepository.updateMostRecentPreKeyId(eq(mostRecentPreKeyId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEverythingSucceeds_thenShouldPropagateSuccess() = runTest {
        val (_, proteusPreKeyRefiller) = arrange {
            withMostRecentPreKeyId(Either.Right(50))

            lowOnPreKeysThreshold = 20
            val generatedPreKeys = listOf(
                PreKeyCrypto(10, "1")
            )

            val prekeys = fakePreKeys(1..10)
            withRemotelyAvailablePreKeysReturning(Either.Right(prekeys))
            withGenerateNewPreKeysReturning(Either.Right(generatedPreKeys))
            withUpdatingMostRecentPrekeyReturning(Either.Right(Unit))
            withUploadNewPrekeyBatchReturning(Either.Right(Unit))
        }

        proteusPreKeyRefiller.refillIfNeeded()
            .shouldSucceed()
    }

    private fun fakePreKeys(preKeyRange: IntRange): List<Int> =
        Array(preKeyRange.last - preKeyRange.first) { preKeyRange.first + it }.toList()

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) :
        PreKeyRepositoryArrangement by PreKeyRepositoryArrangementImpl() {

        var lowOnPreKeysThreshold: Int = ProteusPreKeyRefiller.MINIMUM_PREKEYS_COUNT
        var remotePreKeyTargetCount: Int = ProteusPreKeyRefiller.REMOTE_PREKEYS_TARGET_COUNT
        var maxPreKeyId: Int = ProteusPreKeyRefiller.MAX_PREKEY_ID

        fun arrange() = run {
            runBlocking { configure() }
            this@Arrangement to ProteusPreKeyRefillerImpl(
                preKeyRepository = preKeyRepository,
                lowOnPrekeysTreshold = lowOnPreKeysThreshold,
                remotePreKeyTargetCount = remotePreKeyTargetCount,
                maxPreKeyId = maxPreKeyId
            )
        }
    }

    private companion object {
        fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
