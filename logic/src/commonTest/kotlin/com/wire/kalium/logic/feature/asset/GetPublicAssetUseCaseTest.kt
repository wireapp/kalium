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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPublicAssetUseCaseTest {

    private val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)

    private val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)

    private lateinit var getPublicAsset: GetAvatarAssetUseCase

    @BeforeTest
    fun setUp() {
        getPublicAsset = GetAvatarAssetUseCaseImpl(assetRepository, userRepository)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingGoesWell_thenShouldReturnsASuccessResultWithData() = runTest {
        val assetKey = UserAssetId("value1", "domain")
        val expectedPath = "expected_encrypted_path".toPath()

        everySuspend {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        } returns Either.Right(FetchedAssetData(expectedPath, true))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Success::class, publicAsset::class)
        assertEquals(expectedPath, (publicAsset as PublicAssetResult.Success).assetPath)

        verifySuspend(VerifyMode.exactly(1)) {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        }
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingThereIsAnError_thenShouldReturnsAFailureResultWithRetryEnabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        everySuspend {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        } returns Either.Left(CoreFailure.Unknown(Throwable("an error")))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(CoreFailure.Unknown::class, (publicAsset as PublicAssetResult.Failure).coreFailure::class)
        assertEquals(true, publicAsset.isRetryNeeded)

        verifySuspend(VerifyMode.exactly(1)) {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        }
    }

    @Test
    fun givenACallToGetAPublicAsset_whenThereIsAnNotFoundError_thenShouldReturnsAFailureResultWithRetryDisabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        everySuspend {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        } returns Either.Left(
            NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(
                        404,
                        "asset not found",
                        "asset-not-found"
                    )
                )
            )
        )

        everySuspend {
            userRepository.removeUserBrokenAsset(any())
        } returns Either.Right(Unit)

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(
            NetworkFailure.ServerMiscommunication::class,
            (publicAsset as PublicAssetResult.Failure).coreFailure::class
        )
        assertEquals(false, publicAsset.isRetryNeeded)

        verifySuspend(VerifyMode.exactly(1)) {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            userRepository.removeUserBrokenAsset(any())
        }
    }

    @Test
    fun givenACallToGetAPublicAsset_whenThereIsAnFederatedError_thenShouldReturnsAFailureResultWithRetryDisabled() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        everySuspend {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        } returns Either.Left(NetworkFailure.FederatedBackendFailure.General("error"))

        everySuspend {
            userRepository.removeUserBrokenAsset(any())
        } returns Either.Right(Unit)

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(
            NetworkFailure.FederatedBackendFailure.General::class,
            (publicAsset as PublicAssetResult.Failure).coreFailure::class
        )
        assertEquals(false, publicAsset.isRetryNeeded)

        verifySuspend(VerifyMode.exactly(1)) {
            assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)
        }

        verifySuspend(VerifyMode.not) {
            userRepository.removeUserBrokenAsset(any())
        }
    }

}
