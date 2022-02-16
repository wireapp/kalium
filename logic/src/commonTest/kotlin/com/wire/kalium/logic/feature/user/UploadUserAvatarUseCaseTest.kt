package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

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
    fun givenValidParams_whenUploadingUserAvatar_thenShouldDelegateCallToRepository() = runTest {
        val expected = UploadedAssetId("some_key")
        given(assetRepository)
            .suspendFunction(assetRepository::uploadPublicAsset)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(expected))

        val actual = uploadUserAvatarUseCase("image/jpg", "A".encodeToByteArray())

        verify(assetRepository)
            .suspendFunction(assetRepository::uploadPublicAsset)
            .with(any())
            .wasInvoked(exactly = once)
    }
}
