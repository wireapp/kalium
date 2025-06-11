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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadUserAvatarUseCaseTest {

    @Test
    fun givenValidParams_whenUploadingUserAvatar_thenShouldReturnsASuccessResult() = runTest {
        val expected = UploadedAssetId("some_key", "some_domain")
        val avatarImage = "An Avatar Image (:".encodeToByteArray()
        val avatarPath = "some-image-asset".toPath()
        val (arrangement, uploadUserAvatar) = Arrangement()
            .withStoredData(avatarImage, avatarPath)
            .withSuccessfulUploadResponse(expected)
            .arrange()

        val actual = uploadUserAvatar(avatarPath, avatarImage.size.toLong())

        assertEquals(UploadAvatarResult.Success::class, actual::class)
        assertEquals(expected.key, (actual as UploadAvatarResult.Success).userAssetId.value)

        with(arrangement) {
            coVerify {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), eq(expected.key))
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenUploadAvatarIsInvoked_whenThereIsAnError_thenShouldCallReturnsAFailureResult() = runTest {
        val expectedError = CoreFailure.Unknown(Throwable("an error"))
        val avatarImage = "An Avatar Image (:".encodeToByteArray()
        val avatarPath = "some-image-asset".toPath()
        val (arrangement, uploadUserAvatar) = Arrangement()
            .withStoredData(avatarImage, avatarPath)
            .withErrorResponse(expectedError)
            .arrange()

        val actual = uploadUserAvatar(avatarPath, avatarImage.size.toLong())

        assertEquals(UploadAvatarResult.Failure::class, actual::class)
        assertEquals(CoreFailure.Unknown::class, (actual as UploadAvatarResult.Failure).coreFailure::class)

        with(arrangement) {
            coVerify {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), any())
            }.wasNotInvoked()
        }
    }

    private class Arrangement {
        val assetRepository = mock(AssetRepository::class)
        val userRepository = mock(UserRepository::class)

        val dispatcher = KaliumDispatcherImpl

        private val uploadUserAvatarUseCase: UploadUserAvatarUseCase =
            UploadUserAvatarUseCaseImpl(userRepository, assetRepository, dispatcher)

        var userHomePath = "/Users/me".toPath()

        val fakeFileSystem = FakeFileSystem().also { it.createDirectories(userHomePath) }

        fun withStoredData(data: ByteArray, dataNamePath: Path): Arrangement {
            val fullDataPath = "$userHomePath/$dataNamePath".toPath()
            fakeFileSystem.write(fullDataPath) {
                data
            }
            return this
        }

        suspend fun withSuccessfulUploadResponse(expectedResponse: UploadedAssetId): Arrangement {
            coEvery {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any())
            }.returns(Either.Right(expectedResponse))

            coEvery {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), eq(expectedResponse.key))
            }.returns(Either.Right(Unit))
            return this
        }

        suspend fun withErrorResponse(expectedError: CoreFailure): Arrangement {
            coEvery {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any())
            }.returns(Either.Left(expectedError))
            return this
        }

        fun arrange() = this to uploadUserAvatarUseCase
    }
}
