package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadUserAvatarUseCaseTest {

    @Mock
    private val assetRepository = mock(classOf<AssetRepository>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    private lateinit var uploadUserAvatarUseCase: UploadUserAvatarUseCase

    @BeforeTest
    fun setUp() {
        uploadUserAvatarUseCase = UploadUserAvatarUseCaseImpl(userRepository, assetRepository)
    }

    @Test
    fun givenValidParams_whenUploadingUserAvatar_thenShouldReturnsASuccessResult() = runTest {
        val expected = UploadedAssetId("some_key")
        given(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(expected))

        given(userRepository)
            .suspendFunction(userRepository::updateSelfUser)
            .whenInvokedWith(eq(null), eq(null), eq(expected.key))
            .thenReturn(Either.Right(Unit))

        val actual = uploadUserAvatarUseCase("image/jpg", "A".encodeToByteArray())

        assertEquals(UploadAvatarResult.Success, actual)

        verify(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .with(any())
            .wasInvoked(exactly = once)

        verify(userRepository)
            .suspendFunction(userRepository::updateSelfUser)
            .with(eq(null), eq(null), eq(expected.key))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUploadAvatarIsInvoked_whenThereIsAnError_thenShouldCallReturnsAFailureResult() = runTest {
        val expected = UploadedAssetId("some_key")
        given(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(CoreFailure.Unknown(Throwable("an error"))))

        val actual = uploadUserAvatarUseCase("image/jpg", "A".encodeToByteArray())

        assertEquals(UploadAvatarResult.Failure::class, actual::class)
        assertEquals(CoreFailure.Unknown::class, (actual as UploadAvatarResult.Failure).coreFailure::class)

        verify(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .with(any())
            .wasInvoked(exactly = once)

        verify(userRepository)
            .suspendFunction(userRepository::updateSelfUser)
            .with(eq(null), eq(null), eq(expected.key))
            .wasNotInvoked()
    }
}
