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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPublicAssetUseCaseTest {

    @Mock
    private val assetRepository = mock(AssetRepository::class)

    @Mock
    private val userRepository = mock(UserRepository::class)

    private lateinit var getPublicAsset: GetAvatarAssetUseCase

    @BeforeTest
    fun setUp() {
        getPublicAsset = GetAvatarAssetUseCaseImpl(assetRepository, userRepository)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingGoesWell_thenShouldReturnsASuccessResultWithData() = runTest {
        val assetKey = UserAssetId("value1", "domain")
        val expectedPath = "expected_encrypted_path".toPath()

        coEvery {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.returns(Either.Right(expectedPath))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Success::class, publicAsset::class)
        assertEquals(expectedPath, (publicAsset as PublicAssetResult.Success).assetPath)

        coVerify {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingThereIsAnError_thenShouldReturnsAFailureResultWithRetryEnabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        coEvery {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.returns(Either.Left(CoreFailure.Unknown(Throwable("an error"))))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(CoreFailure.Unknown::class, (publicAsset as PublicAssetResult.Failure).coreFailure::class)
        assertEquals(true, publicAsset.isRetryNeeded)

        coVerify {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenThereIsAnNotFoundError_thenShouldReturnsAFailureResultWithRetryDisabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        coEvery {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.returns(
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            404,
                            "asset not found",
                            "asset-not-found"
                        )
                    )
                )
            )
        )

        coEvery {
            userRepository.removeUserBrokenAsset(any())
        }.returns(Either.Right(Unit))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(NetworkFailure.ServerMiscommunication::class, (publicAsset as PublicAssetResult.Failure).coreFailure::class)
        assertEquals(false, publicAsset.isRetryNeeded)

        coVerify {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.wasInvoked(exactly = once)

        coVerify {
            userRepository.removeUserBrokenAsset(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenThereIsAnFederatedError_thenShouldReturnsAFailureResultWithRetryDisabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        coEvery {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.returns(Either.Left(NetworkFailure.FederatedBackendFailure.General("error")))

        coEvery {
            userRepository.removeUserBrokenAsset(any())
        }.returns(Either.Right(Unit))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(NetworkFailure.FederatedBackendFailure.General::class, (publicAsset as PublicAssetResult.Failure).coreFailure::class)
        assertEquals(false, publicAsset.isRetryNeeded)

        coVerify {
            assetRepository.downloadPublicAsset(eq(assetKey.value), eq(assetKey.domain))
        }.wasInvoked(exactly = once)

        coVerify {
            userRepository.removeUserBrokenAsset(any())
        }.wasNotInvoked()
    }

}
