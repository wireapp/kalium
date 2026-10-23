/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.data.giphy

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifsResponseDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyImageDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyImagesDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphyPaginationDTO
import com.wire.kalium.network.api.base.authenticated.GiphyApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.fakefilesystem.FakeFileSystem

class GiphyRepositoryTest {

    @Test
    fun givenQuery_whenSearching_thenAsksForPgThirteenWithThePageSize() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.search(any(), any(), any(), any(), any()) } returns success(page(gifDto("a")))

        repository.search("cat", offset = 24, language = "de")

        verifySuspend { api.search("cat", 24, 24, "pg-13", "de") }
    }

    @Test
    fun givenGiphyGif_whenMapping_thenUsesDownsizedForSendingAndCleansTheTitle() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.trending(any(), any(), any()) } returns success(page(gifDto("a", title = "Happy Friday Dance GIF by Studio")))

        val gif = assertIs<Either.Right<GifPage>>(repository.trending(0)).value.gifs.single()

        assertEquals("a", gif.id)
        assertEquals("Happy Friday Dance", gif.title)
        assertEquals(GifRendition("https://media.giphy.com/a/downsized.gif", 480, 270, 1_000L), gif.original)
        assertEquals(GifRendition("https://media.giphy.com/a/200w_d.gif", 200, 113, 100L), gif.preview)
    }

    @Test
    fun givenGifsTooLargeOrOfUnknownSize_whenMapping_thenTheyAreSkipped() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.trending(any(), any(), any()) } returns success(
            page(
                gifDto("small"),
                gifDto("too-large", downsizedSize = (3L * 1024 * 1024 + 1).toString()),
                gifDto("unknown-size", downsizedSize = null),
            )
        )

        val page = assertIs<Either.Right<GifPage>>(repository.trending(0)).value

        assertEquals(listOf("small"), page.gifs.map { it.id })
    }

    @Test
    fun givenNoDownsampledRendition_whenMapping_thenPreviewFallsBackToFixedWidth() = runTest {
        val (api, repository) = arrange()
        val dto = gifDto("a").let { it.copy(images = it.images.copy(fixedWidthDownsampled = null)) }
        everySuspend { api.trending(any(), any(), any()) } returns success(page(dto))

        val gif = assertIs<Either.Right<GifPage>>(repository.trending(0)).value.gifs.single()

        assertEquals("https://media.giphy.com/a/200w.gif", gif.preview.url)
    }

    @Test
    fun givenMoreResults_whenMapping_thenNextOffsetFollowsThePage() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.trending(any(), any(), any()) } returns
            success(page(gifDto("a"), pagination = GiphyPaginationDTO(totalCount = 100, count = 24, offset = 24)))

        assertEquals(48, assertIs<Either.Right<GifPage>>(repository.trending(24)).value.nextOffset)
    }

    @Test
    fun givenLastPage_whenMapping_thenThereIsNoNextOffset() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.trending(any(), any(), any()) } returns
            success(page(gifDto("a"), pagination = GiphyPaginationDTO(totalCount = 30, count = 6, offset = 24)))

        assertNull(assertIs<Either.Right<GifPage>>(repository.trending(24)).value.nextOffset)
    }

    @Test
    fun givenOffsetPastGiphysLimit_whenMapping_thenThereIsNoNextOffset() = runTest {
        val (api, repository) = arrange()
        everySuspend { api.trending(any(), any(), any()) } returns
            success(page(gifDto("a"), pagination = GiphyPaginationDTO(totalCount = 100_000, count = 24, offset = 4_990)))

        assertNull(assertIs<Either.Right<GifPage>>(repository.trending(4_990)).value.nextOffset)
    }

    @Test
    fun givenGiphyUrl_whenDownloading_thenWritesATemporaryFile() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        val (api, repository) = arrange(fileSystem)
        everySuspend { api.downloadGif(any(), any(), any()) } calls { invocation ->
            (invocation.args[2] as Sink).write(Buffer().writeUtf8("GIF89a"), 6)
            NetworkResponse.Success(6L, emptyMap(), 200)
        }

        val downloaded = assertIs<Either.Right<DownloadedGif>>(repository.download(gif())).value

        assertEquals(6L, downloaded.sizeInBytes)
        assertEquals("GIF89a", fileSystem.readByteArray(downloaded.path).decodeToString())
        verifySuspend { api.downloadGif("https://media.giphy.com/a/downsized.gif", 3L * 1024 * 1024, any()) }
    }

    @Test
    fun givenFailedDownload_whenDownloading_thenDeletesThePartialFile() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        val (api, repository) = arrange(fileSystem)
        everySuspend { api.downloadGif(any(), any(), any()) } calls { invocation ->
            (invocation.args[2] as Sink).write(Buffer().writeUtf8("GIF"), 3)
            NetworkResponse.Error(KaliumException.GenericError(IllegalStateException("too large")))
        }

        assertIs<Either.Left<*>>(repository.download(gif()))

        assertFalse(fileSystem.listDirectories(fileSystem.rootCachePath).any { it.name.startsWith("giphy-") })
    }

    @Test
    fun givenDownloadCancelled_whenDownloading_thenDeletesThePartialFile() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        val (api, repository) = arrange(fileSystem)
        val started = CompletableDeferred<Unit>()
        everySuspend { api.downloadGif(any(), any(), any()) } calls { invocation ->
            (invocation.args[2] as Sink).write(Buffer().writeUtf8("GIF"), 3)
            started.complete(Unit)
            awaitCancellation()
        }

        val download = launch { repository.download(gif()) }
        started.await()
        download.cancelAndJoin()

        assertFalse(fileSystem.listDirectories(fileSystem.rootCachePath).any { it.name.startsWith("giphy-") })
    }

    @Test
    fun givenCacheFolderMissing_whenDownloading_thenFailsWithAStorageFailure() = runTest {
        val fakeFileSystem = FakeFileSystem()
        val fileSystem = FakeKaliumFileSystem(fakeFileSystem)
        fakeFileSystem.deleteRecursively(fileSystem.rootCachePath)
        val (_, repository) = arrange(fileSystem)

        val failure = assertIs<Either.Left<*>>(repository.download(gif())).value

        assertIs<StorageFailure.Generic>(failure)
    }

    @Test
    fun givenTitles_whenCleaning_thenOnlyTheGiphySuffixIsRemoved() {
        assertEquals("Happy Friday", GiphyDataSource.cleanTitle("Happy Friday GIF"))
        assertEquals("Cat", GiphyDataSource.cleanTitle("Cat gif by The Cat Studio"))
        assertEquals("GIF", GiphyDataSource.cleanTitle("GIF"))
        assertEquals("Gifted kid", GiphyDataSource.cleanTitle("Gifted kid"))
    }

    private fun arrange(fileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem()): Pair<GiphyApi, GiphyRepository> {
        val api = mock<GiphyApi>()
        return api to GiphyDataSource(api, fileSystem)
    }

    private fun success(response: GiphyGifsResponseDTO) = NetworkResponse.Success(response, emptyMap(), 200)

    private fun page(vararg gifs: GiphyGifDTO, pagination: GiphyPaginationDTO? = null) =
        GiphyGifsResponseDTO(data = gifs.toList(), pagination = pagination)

    private fun gifDto(id: String, title: String = "Title", downsizedSize: String? = "1000") = GiphyGifDTO(
        id = id,
        title = title,
        images = GiphyImagesDTO(
            downsized = GiphyImageDTO("https://media.giphy.com/$id/downsized.gif", "480", "270", downsizedSize),
            fixedWidth = GiphyImageDTO("https://media.giphy.com/$id/200w.gif", "200", "113", "300"),
            fixedWidthDownsampled = GiphyImageDTO("https://media.giphy.com/$id/200w_d.gif", "200", "113", "100"),
        )
    )

    private fun gif() = Gif(
        id = "a",
        title = "Title",
        preview = GifRendition("https://media.giphy.com/a/200w_d.gif", 200, 113, 100L),
        original = GifRendition("https://media.giphy.com/a/downsized.gif", 480, 270, 1_000L),
    )
}
