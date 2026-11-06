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
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerateLinkPreviewUseCaseTest {

    @Test
    fun givenClaudeArticle_whenGeneratingPreview_thenFetchesCompleteUrl() = runTest {
        val url = "https://claude.dev/blog/solving-code-review-at-anthropic/"
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()
        everySuspend { repository.fetchOpenGraph(url, url) } returns Either.Right(
            OpenGraphData(
                title = "How the Claude Code team does code review",
                url = url,
                description = "Code review at Anthropic",
                imageUrls = emptyList(),
                siteName = "claude.dev Blog"
            )
        )
        everySuspend { userPropertyRepository.getLinkPreviewsStatus() } returns true

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = true, scope = this)
            .invoke("read $url")

        assertNotNull(result)
        assertEquals(url, result.url)
        assertEquals(url, result.permanentUrl)
        assertEquals(5, result.urlOffset)
        verifySuspend { repository.fetchOpenGraph(url, url) }
    }

    @Test
    fun givenOpenGraphImage_whenGeneratingPreview_thenImageIsAttached() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()
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
        everySuspend { userPropertyRepository.getLinkPreviewsStatus() } returns true

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = true, scope = this)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertEquals(expectedImage, result.image)
    }

    @Test
    fun givenNoOpenGraphImage_whenGeneratingPreview_thenPreviewHasNoImage() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()
        everySuspend { repository.fetchOpenGraph(any(), any()) } returns Either.Right(
            OpenGraphData(
                title = "Title",
                url = "https://example.com/permalink",
                description = "Description",
                imageUrls = emptyList(),
                siteName = "Example"
            )
        )
        everySuspend { userPropertyRepository.getLinkPreviewsStatus() } returns true

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = true, scope = this)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertNull(result.image)
    }

    @Test
    fun givenImageFetchFailure_whenGeneratingPreview_thenPreviewHasNoImage() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()
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
        everySuspend { userPropertyRepository.getLinkPreviewsStatus() } returns true

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = true, scope = this)
            .invoke("see https://example.com")

        assertNotNull(result)
        assertNull(result.image)
    }

    @Test
    fun givenLinkPreviewDisabled_whenInvoked_thenReturnsNullWithoutCallingRepository() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = false, scope = this)
            .invoke("see https://example.com")

        assertNull(result)
        verifySuspend(VerifyMode.not) {
            repository.fetchOpenGraph(any(), any())
        }
    }

    @Test
    fun givenUserLinkPreviewsDisabled_whenInvoked_thenReturnsNullWithoutCallingRepository() = runTest {
        val repository = mock<LinkPreviewRepository>()
        val userPropertyRepository = mock<UserPropertyRepository>()
        everySuspend { userPropertyRepository.getLinkPreviewsStatus() } returns false

        val result = GenerateLinkPreviewUseCaseImpl(repository, userPropertyRepository, linkPreviewEnabled = true, scope = this)
            .invoke("see https://example.com")

        assertNull(result)
        verifySuspend(VerifyMode.not) {
            repository.fetchOpenGraph(any(), any())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenAccountOrCallerCancellation_whenGenerating_thenAwaitsCleanupAndStopsAccountWork() = runTest {
        listOf(true, false).forEach { cancelAccount ->
            val accountJob = SupervisorJob()
            val accountScope = CoroutineScope(coroutineContext + accountJob)
            val started = CompletableDeferred<Unit>()
            val cleanedUp = CompletableDeferred<Unit>()
            val repository = mock<LinkPreviewRepository>()
            val userProperties = mock<UserPropertyRepository>()
            everySuspend { userProperties.getLinkPreviewsStatus() } returns true
            everySuspend { repository.fetchOpenGraph(any(), any()) } calls {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        delay(100)
                        cleanedUp.complete(Unit)
                    }
                }
            }
            val useCase = GenerateLinkPreviewUseCaseImpl(repository, userProperties, true, accountScope)
            val request = async { useCase("https://example.com") }
            started.await()

            if (cancelAccount) accountJob.cancel() else request.cancel()
            runCurrent()
            assertFalse(request.isCompleted)
            request.join()

            assertTrue(cleanedUp.isCompleted)
            assertFailsWith<CancellationException> { request.await() }
            accountJob.cancel()
            accountJob.join()
            assertFailsWith<CancellationException> { useCase("https://example.com/after-logout") }
            verifySuspend(VerifyMode.exactly(1)) { repository.fetchOpenGraph(any(), any()) }
        }
    }

}
