package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.self.SelfUserRepository
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
    private val selfUserRepository = mock(classOf<SelfUserRepository>())

    private lateinit var uploadUserAvatarUseCase: UploadUserAvatarUseCase

    @BeforeTest
    fun setUp() {
        uploadUserAvatarUseCase = UploadUserAvatarUseCaseImpl(selfUserRepository, assetRepository)
    }

    @Test
    fun givenValidParams_whenUploadingUserAvatar_thenShouldReturnsASuccessResult() = runTest {
        val expected = UploadedAssetId("some_key")
        given(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(expected))

        given(selfUserRepository)
            .suspendFunction(selfUserRepository::updateSelfUser)
            .whenInvokedWith(eq(null), eq(null), eq(expected.key))
            .thenReturn(Either.Right(stubSelfUser()))

        val actual = uploadUserAvatarUseCase("A".encodeToByteArray())

        assertEquals(UploadAvatarResult.Success::class, actual::class)
        assertEquals("some_key", (actual as UploadAvatarResult.Success).userAssetId)

        verify(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(selfUserRepository)
            .suspendFunction(selfUserRepository::updateSelfUser)
            .with(eq(null), eq(null), eq(expected.key))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUploadAvatarIsInvoked_whenThereIsAnError_thenShouldCallReturnsAFailureResult() = runTest {
        val expected = UploadedAssetId("some_key")
        given(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Left(CoreFailure.Unknown(Throwable("an error"))))

        val actual = uploadUserAvatarUseCase("A".encodeToByteArray())

        assertEquals(UploadAvatarResult.Failure::class, actual::class)
        assertEquals(CoreFailure.Unknown::class, (actual as UploadAvatarResult.Failure).coreFailure::class)

        verify(assetRepository)
            .suspendFunction(assetRepository::uploadAndPersistPublicAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(selfUserRepository)
            .suspendFunction(selfUserRepository::updateSelfUser)
            .with(eq(null), eq(null), eq(expected.key))
            .wasNotInvoked()
    }

    private fun stubSelfUser() = SelfUser(
        UserId("some_id", "some_domain"),
        "some_name",
        "some_handle",
        "some_email",
        null,
        1,
        null,
        ConnectionState.ACCEPTED,
        "some_key",
        "some_key",
        UserAvailabilityStatus.NONE
    )
}
