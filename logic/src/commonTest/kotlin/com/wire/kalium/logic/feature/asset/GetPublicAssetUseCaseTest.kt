package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetPublicAssetUseCaseTest {

    @Mock
    private val assetRepository = mock(classOf<AssetRepository>())

    private lateinit var getPublicAsset: GetAvatarAssetUseCase

    @BeforeTest
    fun setUp() {
        getPublicAsset = GetAvatarAssetUseCaseImpl(assetRepository)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingGoesWell_thenShouldReturnsASuccessResultWithData() = runTest {
        val assetKey = UserAssetId("value1", "domain")
        val expectedPath = "expected_encrypted_path".toPath()

        given(assetRepository)
            .suspendFunction(assetRepository::downloadPublicAsset)
            .whenInvokedWith(eq(assetKey))
            .thenReturn(Either.Right(expectedPath))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Success::class, publicAsset::class)
        assertEquals(expectedPath, (publicAsset as PublicAssetResult.Success).assetPath)

        verify(assetRepository)
            .suspendFunction(assetRepository::downloadPublicAsset)
            .with(eq(assetKey))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenACallToGetAPublicAsset_whenEverythingThereIsAnError_thenShouldReturnsAFailureResult() = runTest {
        val assetKey = UserAssetId("value1", "domain")

        given(assetRepository)
            .suspendFunction(assetRepository::downloadPublicAsset)
            .whenInvokedWith(eq(assetKey))
            .thenReturn(Either.Left(CoreFailure.Unknown(Throwable("an error"))))

        val publicAsset = getPublicAsset(assetKey)

        assertEquals(PublicAssetResult.Failure::class, publicAsset::class)
        assertEquals(CoreFailure.Unknown::class, (publicAsset as PublicAssetResult.Failure).coreFailure::class)

        verify(assetRepository)
            .suspendFunction(assetRepository::downloadPublicAsset)
            .with(eq(assetKey))
            .wasInvoked(exactly = once)
    }
}
