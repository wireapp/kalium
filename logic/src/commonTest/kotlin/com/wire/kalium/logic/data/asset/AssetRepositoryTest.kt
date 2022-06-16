package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AssetRepositoryTest {

    val fakeFileSystem = FakeFileSystem()

    @Mock
    private val assetApi = mock(classOf<AssetApi>())

    @Mock
    private val assetDAO = mock(classOf<AssetDAO>())

    private lateinit var assetRepository: AssetRepository

    private val assetMapper by lazy { AssetMapperImpl() }

    @BeforeTest
    fun setUp() {
        assetRepository = AssetDataSource(assetApi, assetDAO, assetMapper)
    }

    @Test
    fun givenValidParams_whenUploadingPublicAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        given(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .whenInvokedWith(any())
            .thenDoNothing()

        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    AssetResponse("some_key", "some_domain", "some_expiration_val", "some_token"),
                    mapOf(),
                    200
                )
            )

        val actual = assetRepository.uploadAndPersistPublicAsset(rawAssetData = "the_image".encodeToByteArray(), mimeType = ImageAsset.JPEG)

        actual.shouldSucceed {
            assertEquals("some_key", it.key)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidParams_whenUploadingPrivateAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        given(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .whenInvokedWith(any())
            .thenDoNothing()

        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    AssetResponse("some_key", "some_domain", "some_expiration_val", "some_token"),
                    mapOf(),
                    200
                )
            )

        val actual =
            assetRepository.uploadAndPersistPrivateAsset(encryptedAssetDataPath = "the_image".encodeToByteArray(), mimeType = ImageAsset.JPEG)

        actual.shouldSucceed {
            assertEquals("some_key", it.key)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenUploadingPublicAssets_thenShouldFail() = runTest {
        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val actual = assetRepository.uploadAndPersistPublicAsset(rawAssetData = "the_image".encodeToByteArray(), mimeType = ImageAsset.JPEG)

        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenUploadingPrivateAssets_thenShouldFail() = runTest {
        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val actual =
            assetRepository.uploadAndPersistPrivateAsset(encryptedAssetDataPath = "the_image".toPath(), mimeType = ImageAsset.JPEG)

        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAListOfAssets_whenSavingAssets_thenShouldSucceed() = runTest {
        val assetsIdToPersist = listOf(AssetId("value1", "domain1"), AssetId("value2", "domain2"))
        val dummyPath = "dummy_path".toPath()
        val expectedDataSource = fakeFileSystem.source(dummyPath)

        assetsIdToPersist.forEach { assetKey ->
            mockAssetDaoGetByKeyCall(assetKey, null)
            given(assetApi)
                .suspendFunction(assetApi::downloadAsset)
                .whenInvokedWith(matching { assetId -> assetId.value == assetKey.value }, eq(null))
                .thenReturn(NetworkResponse.Success(expectedDataSource, mapOf(), 200))

            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        val actual = assetRepository.downloadUsersPictureAssets(assetsIdToPersist)

        actual.shouldSucceed {
            assertEquals(it, Unit)
        }

        verify(assetDAO).suspendFunction(assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenAnAssetId_whenDownloadingAssetsAndNotPresentInDB_thenShouldReturnItsBinaryDataFromRemoteAndPersistIt() = runTest {
        val assetKey = UserAssetId("value1", "domain1")
        val dummyPath = "dummy_path".toPath()
        val expectedDataSource = fakeFileSystem.source(dummyPath)

        mockAssetDaoGetByKeyCall(assetKey, null)
        given(assetApi)
            .suspendFunction(assetApi::downloadAsset)
            .whenInvokedWith(matching { it.value == assetKey.value }, eq(null))
            .thenReturn(NetworkResponse.Success(expectedDataSource, mapOf(), 200))
        given(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .whenInvokedWith(any())
            .thenReturn(Unit)

        assetRepository.downloadPublicAsset(assetKey)

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(eq(assetKey.value))
            .wasInvoked(exactly = once)
        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(matching { it.value == assetKey.value }, eq(null))
            .wasInvoked(exactly = once)
        verify(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenDownloadingAssets_thenShouldReturnThrowNetworkFailure() = runTest {
        val assetKey = NetworkQualifiedId("value1", "domain1")
        mockAssetDaoGetByKeyCall(UserAssetId("value1", "domain1"), null)
        given(assetApi)
            .suspendFunction(assetApi::downloadAsset)
            .whenInvokedWith(eq(assetKey), any(), eq(null))
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val actual = assetRepository.downloadPublicAsset(UserAssetId("value1", "domain1"))

        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(matching { it == assetKey.value })
            .wasInvoked(exactly = once)

        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(eq(assetKey), any(), eq(null))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnAssetId_whenAssetIsAlreadyDownloaded_thenShouldReturnItsBinaryDataFromDB() = runTest {
        val assetKey = UserAssetId("value1", "domain1")
        val expectedImage = "my_image_asset".toByteArray()
        mockAssetDaoGetByKeyCall(UserAssetId("value1", "domain1"), stubAssetEntity(assetKey.value, expectedImage))

        assetRepository.downloadPublicAsset(assetKey)

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(eq(assetKey.value))
            .wasInvoked(exactly = once)

        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(matching { it.value == assetKey.value }, eq(null))
            .wasNotInvoked()
    }

    private fun mockAssetDaoGetByKeyCall(assetKey: UserAssetId, expectedAssetEntity: AssetEntity?) {
        given(assetDAO)
            .suspendFunction(assetDAO::getAssetByKey)
            .whenInvokedWith(eq(assetKey.value))
            .thenReturn(flowOf(expectedAssetEntity))
    }

    private fun stubAssetEntity(assetKey: String, rawData: ByteArray) =
        AssetEntity(assetKey, "domain", null, rawData, null, 1)
}
