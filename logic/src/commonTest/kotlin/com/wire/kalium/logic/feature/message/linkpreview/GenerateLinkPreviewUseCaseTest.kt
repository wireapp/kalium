/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.message.linkpreview

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewRepository
import com.wire.kalium.logic.data.message.linkpreview.OpenGraphData
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerateLinkPreviewUseCaseTest {

    @Test
    fun givenOpenGraphImage_whenGeneratingPreview_thenImageIsAttached() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val expectedImage = LinkPreviewAsset(
            mimeType = "image/png",
            assetDataPath = "/tmp/link-preview.png".toPath(),
            assetDataSize = 32,
            assetHeight = 1,
            assetWidth = 1
        )
        everySuspend { repository.fetchOpenGraph(any(), any()) } returns Either.Right(
            OpenGraphData(
                title = "Title",
                url = "https://example.com/permalink",
                description = "Description",
                imageUrls = listOf("https://example.com/image.png"),
                siteName = "Example"
            )
        )
        everySuspend { repository.fetchImage("https://example.com/image.png") } returns Either.Right(expectedImage)

        val result = GenerateLinkPreviewUseCaseImpl(repository, linkPreviewEnabled = true)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertEquals(expectedImage, result.image)
    }

    @Test
    fun givenNoOpenGraphImage_whenGeneratingPreview_thenPreviewHasNoImage() = runTest {
        val repository = mock<LinkPreviewRepository>()
        everySuspend { repository.fetchOpenGraph(any(), any()) } returns Either.Right(
            OpenGraphData(
                title = "Title",
                url = "https://example.com/permalink",
                description = "Description",
                imageUrls = emptyList(),
                siteName = "Example"
            )
        )

        val result = GenerateLinkPreviewUseCaseImpl(repository, linkPreviewEnabled = true)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertNull(result.image)
    }

    @Test
    fun givenImageFetchFailure_whenGeneratingPreview_thenPreviewHasNoImage() = runTest {
        val repository = mock<LinkPreviewRepository>()
        everySuspend { repository.fetchOpenGraph(any(), any()) } returns Either.Right(
            OpenGraphData(
                title = "Title",
                url = "https://example.com/permalink",
                description = "Description",
                imageUrls = listOf("https://example.com/image.png"),
                siteName = "Example"
            )
        )
        everySuspend { repository.fetchImage("https://example.com/image.png") } returns Either.Left(
            NetworkFailure.NoNetworkConnection(null)
        )

        val result = GenerateLinkPreviewUseCaseImpl(repository, linkPreviewEnabled = true)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertNull(result.image)
    }

    @Test
    fun givenLinkPreviewDisabled_whenInvoked_thenReturnsNullWithoutCallingRepository() = runTest {
        val repository = mock<LinkPreviewRepository>()

        val result = GenerateLinkPreviewUseCaseImpl(repository, linkPreviewEnabled = false)
            .invoke("see https://example.com")

        assertNull(result)
        verifySuspend(VerifyMode.not) {
            repository.fetchOpenGraph(any(), any())
        }
    }
}
