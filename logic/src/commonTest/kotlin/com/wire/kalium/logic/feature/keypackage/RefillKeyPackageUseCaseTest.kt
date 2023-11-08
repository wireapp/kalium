/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RefillKeyPackageUseCaseTest {

    @Test
    fun givenRefillKeyPackageUseCase_WhenNeedRefillReturnTrue_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD - 1).toInt()

        val (arrangement, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount)
            .withKeyPackageCount(keyPackageCount)
            .withUploadKeyPackagesSuccessful()
            .arrange()

        val actual = refillKeyPackagesUseCase()

        verify(arrangement.keyPackageRepository).coroutine {
            uploadNewKeyPackages(TestClient.CLIENT_ID, Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount)
        }.wasInvoked(once)

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenRefillKeyPackageUseCase_WhenNeedRefillReturnFalse_ThenRequestToRefillKeyPackageIsPerformed() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD).toInt()

        val (_, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(false, 0)
            .withKeyPackageCount(keyPackageCount)
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Success>(actual)
    }

    @Test
    fun givenErrorIsEncountered_ThenFailureIsPropagated() = runTest {
        val networkFailure = NetworkFailure.NoNetworkConnection(null)

        val (_, refillKeyPackagesUseCase) = Arrangement()
            .withExistingSelfClientId()
            .withKeyPackageLimits(true, 0)
            .withGetAvailableKeyPackagesFailing(networkFailure)
            .arrange()

        val actual = refillKeyPackagesUseCase()

        assertIs<RefillKeyPackagesResult.Failure>(actual)
        assertEquals(actual.failure, networkFailure)
    }

    private class Arrangement {
        @Mock
        val keyPackageRepository = mock(classOf<KeyPackageRepository>())

        @Mock
        val keyPackageLimitsProvider = mock(classOf<KeyPackageLimitsProvider>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        private var refillKeyPackageUseCase = RefillKeyPackagesUseCaseImpl(
            keyPackageRepository,
            keyPackageLimitsProvider,
            currentClientIdProvider
        )

        fun withExistingSelfClientId() = apply {
            given(currentClientIdProvider).suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .then { Either.Right(TestClient.CLIENT_ID) }
        }

        fun withKeyPackageLimits(needRefill: Boolean, refillAmount: Int) = apply {
            given(keyPackageLimitsProvider).function(keyPackageLimitsProvider::needsRefill)
                .whenInvokedWith(anything())
                .thenReturn(needRefill)
            given(keyPackageLimitsProvider).function(keyPackageLimitsProvider::refillAmount)
                .whenInvoked()
                .thenReturn(refillAmount)
        }

        fun withKeyPackageCount(count: Int) = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
                .whenInvokedWith(anything())
                .then { Either.Right(KeyPackageCountDTO(count)) }
        }

        fun withUploadKeyPackagesSuccessful() = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::uploadNewKeyPackages)
                .whenInvokedWith(eq(TestClient.CLIENT_ID), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetAvailableKeyPackagesFailing(failure: NetworkFailure) = apply {
            given(keyPackageRepository).suspendFunction(keyPackageRepository::getAvailableKeyPackageCount)
                .whenInvokedWith(anything())
                .then { Either.Left(failure) }
        }

        fun arrange() = this to refillKeyPackageUseCase

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
        }

    }

}
