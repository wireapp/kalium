package com.wire.kalium.http.request

import com.wire.kalium.network.api.base.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.model.AssetRetentionType
import com.wire.kalium.network.api.v0.authenticated.StreamAssetContent
import io.ktor.utils.io.writer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.IOException
import okio.Path.Companion.toPath
import okio.Source
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class ByteChannelRequestBodyTest {

    @Test
    fun givenStreamAssetContent_whenWritingToChannelTheJobGetsCancelled_thenFailWithIoExceptionContaitinCancellationMessage() =
        runTest(StandardTestDispatcher()) {
            val exception = assertFails {
                val parentJob = Job()

                withContext(parentJob) {
                    val fileSystem = FakeFileSystem()
                    val encryptedData = "some-data".encodeToByteArray()
                    val encryptedDataSource = { getDummyDataSource(fileSystem, encryptedData) }
                    val assetMetadata = AssetMetadataRequest("image/jpeg", true, AssetRetentionType.ETERNAL, "md5-hash")

                    launch {
                        val streamAssetContent = StreamAssetContent(
                            assetMetadata,
                            encryptedData.size.toLong(),
                            encryptedDataSource,
                            coroutineContext
                        )

                        writer(coroutineContext) {
                            // cancel before writeTo
                            parentJob.cancel()
                            streamAssetContent.writeTo(channel)
                        }
                    }

                    runCurrent()
                }
            }

            assertIs<IOException>(exception)
            assertEquals("Job was cancelled", exception.message)
        }

    private fun getDummyDataSource(fileSystem: FakeFileSystem, dummyData: ByteArray): Source {
        val dummyPath = "some-data-path".toPath()
        fileSystem.write(dummyPath) {
            write(dummyData)
        }
        return fileSystem.source(dummyPath)
    }

}
