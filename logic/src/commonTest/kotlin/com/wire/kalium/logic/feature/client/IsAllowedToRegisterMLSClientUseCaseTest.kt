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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsAllowedToRegisterMLSClientUseCaseTest {

    @Test
    fun givenAllMlsConditionsAreMet_whenUseCaseInvoked_returnsTrue() = runTest {
        // given
        val (_, isAllowedToRegisterMLSClientUseCase) = Arrangement()
            .withMlsFeatureFlag(true)
            .withUserConfigMlsEnabled(true)
            .withGetPublicKeysSuccessful()
            .arrange()

        // when
        val result = isAllowedToRegisterMLSClientUseCase()

        // then
        assertEquals(true, result)
    }

    @Test
    fun givenMlsFeatureFlagDisabled_whenUseCaseInvoked_returnsFalse() = runTest {
        // given
        val (_, isAllowedToRegisterMLSClientUseCase) = Arrangement()
            .withMlsFeatureFlag(false)
            .withUserConfigMlsEnabled(true)
            .withGetPublicKeysSuccessful()
            .arrange()

        // when
        val result = isAllowedToRegisterMLSClientUseCase()

        // then
        assertEquals(false, result)
    }

    @Test
    fun givenUserConfigMlsDisabled_whenUseCaseInvoked_returnsFalse() = runTest {
        // given
        val (_, isAllowedToRegisterMLSClientUseCase) = Arrangement()
            .withMlsFeatureFlag(true)
            .withUserConfigMlsEnabled(false)
            .withGetPublicKeysSuccessful()
            .arrange()

        // when
        val result = isAllowedToRegisterMLSClientUseCase()

        // then
        assertEquals(false, result)
    }

    @Test
    fun givenPublicKeysFailure_whenUseCaseInvoked_returnsFalse() = runTest {
        // given
        val (_, isAllowedToRegisterMLSClientUseCase) = Arrangement()
            .withMlsFeatureFlag(true)
            .withUserConfigMlsEnabled(true)
            .withGetPublicKeysFailed()
            .arrange()

        // when
        val result = isAllowedToRegisterMLSClientUseCase()

        // then
        assertEquals(false, result)
    }

    @Test
    fun givenUserConfigDataNotFound_whenUseCaseInvoked_returnsFalse() = runTest {
        // given
        val (_, isAllowedToRegisterMLSClientUseCase) = Arrangement()
            .withMlsFeatureFlag(true)
            .withUserConfigDataNotFound()
            .withGetPublicKeysFailed()
            .arrange()

        // when
        val result = isAllowedToRegisterMLSClientUseCase()

        // then
        assertEquals(false, result)
    }


    private class Arrangement {
        @Mock
        val featureSupport = mock(FeatureSupport::class)

        @Mock
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun withMlsFeatureFlag(enabled: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(enabled)
        }

        suspend fun withUserConfigMlsEnabled(enabled: Boolean) = apply {
            coEvery {
                userConfigRepository.isMLSEnabled()
            }.returns(Either.Right(enabled))
        }

        suspend fun withUserConfigDataNotFound() = apply {
            coEvery {
                userConfigRepository.isMLSEnabled()
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withGetPublicKeysSuccessful() = apply {
            coEvery {
                mlsPublicKeysRepository.getKeys()
            }.returns(Either.Right(MLS_PUBLIC_KEY))
        }

        suspend fun withGetPublicKeysFailed() = apply {
            coEvery {
                mlsPublicKeysRepository.getKeys()
            }.returns(Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        fun arrange() = this to IsAllowedToRegisterMLSClientUseCaseImpl(
            featureSupport = featureSupport,
            mlsPublicKeysRepository = mlsPublicKeysRepository,
            userConfigRepository = userConfigRepository
        )

        companion object {
            val MLS_PUBLIC_KEY = MLSPublicKeys(
                removal = mapOf(
                    "ed25519" to "gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw="
                )
            )
        }
    }
}
