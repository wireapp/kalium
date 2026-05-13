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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            verifySuspend(VerifyMode.exactly(1)) {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any(), any(), any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), eq(expected.key))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any(), any(), any(), any())
            }

            verifySuspend(VerifyMode.not) {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), any())
            }
        }
    }

    @Test
    fun givenValidParams_whenUploadingUserAvatar_thenShouldPassCorrectMetadata() = runTest {
        val expected = UploadedAssetId("some_key", "some_domain")
        val avatarImage = "An Avatar Image (:".encodeToByteArray()
        val avatarPath = "some-image-asset".toPath()
        val (arrangement, uploadUserAvatar) = Arrangement()
            .withStoredData(avatarImage, avatarPath)
            .withSuccessfulUploadResponse(expected)
            .arrange()

        uploadUserAvatar(avatarPath, avatarImage.size.toLong())

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                assetRepository.uploadAndPersistPublicAsset(
                    mimeType = eq("image/jpg"),
                    assetDataPath = any(),
                    assetDataSize = eq(avatarImage.size.toLong()),
                    conversationId = eq(null),
                    filename = eq("profile-picture"),
                    filetype = eq("image/jpg")
                )
            }
        }
    }

    private class Arrangement {
        val assetRepository = mock<AssetRepository>()
        val userRepository = mock<UserRepository>()

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
            everySuspend {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any(), any(), any(), any())
            } returns Either.Right(expectedResponse)

            everySuspend {
                userRepository.updateSelfUser(eq<String?>(null), eq<Int?>(null), eq(expectedResponse.key))
            } returns Either.Right(Unit)
            return this
        }

        suspend fun withErrorResponse(expectedError: CoreFailure): Arrangement {
            everySuspend {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any(), any(), any(), any())
            } returns Either.Left(expectedError)
            return this
        }

        fun arrange() = this to uploadUserAvatarUseCase
    }
}
