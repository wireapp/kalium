package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.ImageAsset
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.internal.ThreadSafeHeapNode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UploadPrivateAssetUseCaseTest {

    @Test
    fun givenValidParams_whenUploadingPrivateAsset_thenShouldReturnASuccessResult() = runTest {
        // Given
        val expected = UploadedAssetId("some_key")
        val dummyByteArray = "A".encodeToByteArray()
        val mimeType = ImageAsset.JPEG
        val arrangement = Arrangement()
            .withSuccessfulResponse(expected)
            .arrange()

        // When
        val result = arrangement.uploadPrivateAssetUseCase(mimeType, dummyByteArray)

        // Then
        result.fold({
            assertNull(it)
        }) {
            assertEquals(expected.key, it.key)
        }
    }

    @Test
    fun givenUploadPrivateAssetIsInvoked_whenThereIsAnError_thenShouldCallReturnsAFailureResult() = runTest {
        // Given
        val expectedException = CoreFailure.Unknown(Exception("Unexpected Error"))
        val dummyByteArray = "A".encodeToByteArray()
        val mimeType = ImageAsset.JPEG
        val arrangement = Arrangement()
            .withError(expectedException)
            .arrange()

        // When
        val result = arrangement.uploadPrivateAssetUseCase(mimeType, dummyByteArray)

        // Then
        result.fold({
            assertEquals(it, expectedException)
        }) {
            assertNull(it)
        }
    }

    private class Arrangement {
        @Mock
        val assetRepository = mock(classOf<AssetRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        private fun fakeSelfUser() = SelfUser(
            UserId("some_id", "some_domain"),
            "some_name",
            "some_handle",
            "some_email",
            null,
            1,
            null,
            "some_key",
            "some_key"
        )

        val uploadPrivateAssetUseCase = UploadPrivateAssetUseCaseImpl(userRepository, assetRepository)

        fun withSuccessfulResponse(expectedResponse: UploadedAssetId): Arrangement {
            given(assetRepository)
                .suspendFunction(assetRepository::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(expectedResponse))

            given(userRepository)
                .suspendFunction(userRepository::updateSelfUser)
                .whenInvokedWith(eq(null), eq(null), eq(expectedResponse.key))
                .thenReturn(Either.Right(fakeSelfUser()))
            return this
        }
        fun withError(genericError: CoreFailure): Arrangement {
            given(assetRepository)
                .suspendFunction(assetRepository::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(genericError))
            return this
        }

        fun arrange() = this
    }
}
