/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.giphy.DownloadedGif
import com.wire.kalium.logic.data.giphy.Gif
import com.wire.kalium.logic.data.giphy.GifRendition
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.upload.AssetUploadParams
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class SendGifMessageUseCaseTest {

    @Test
    fun givenCaptionAndReply_whenSending_thenSendsTheQuotingCaptionBeforeTheGif() = runTest {
        val arrangement = Arrangement()

        val result = arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "happy friday · via giphy.com", "quoted-id")

        assertEquals(SendGifMessageResult.Success, result)
        assertEquals(listOf("text:happy friday · via giphy.com:quoted-id", "gif"), arrangement.events)
        val asset = arrangement.sentAssets.single()
        assertEquals(CONVERSATION_ID, asset.conversationId)
        assertEquals(DOWNLOADED_GIF.path, asset.assetDataPath)
        assertEquals(DOWNLOADED_GIF.sizeInBytes, asset.assetDataSize)
        assertEquals("image/gif", asset.assetMimeType)
        assertEquals("giphy-abc.gif", asset.assetName)
        assertEquals(480, asset.assetWidth)
        assertEquals(270, asset.assetHeight)
    }

    @Test
    fun givenNoCaption_whenSending_thenSendsOnlyTheGif() = runTest {
        val arrangement = Arrangement()

        assertEquals(SendGifMessageResult.Success, arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, null, "quoted-id"))

        assertEquals(listOf("gif"), arrangement.events)
    }

    @Test
    fun givenFileSharingDisabled_whenSending_thenSendsNothing() = runTest {
        val arrangement = Arrangement(fileSharing = FileSharingStatus.Value.Disabled)

        assertEquals(SendGifMessageResult.DisabledByTeam, arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null))

        assertTrue(arrangement.events.isEmpty())
    }

    @Test
    fun givenGifsNotAllowedByTheTeam_whenSending_thenSendsNothing() = runTest {
        val arrangement = Arrangement(fileSharing = FileSharingStatus.Value.EnabledSome(listOf("pdf")), gifAllowed = false)

        assertEquals(SendGifMessageResult.RestrictedFileType, arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null))

        assertTrue(arrangement.events.isEmpty())
    }

    @Test
    fun givenCaptionNotStored_whenSending_thenTheGifIsNotSent() = runTest {
        val failure = CoreFailure.Unknown(null)
        val arrangement = Arrangement(captionResult = MessageOperationResult.Failure(failure, messageId = null))

        assertEquals(SendGifMessageResult.Failure(failure), arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null))

        assertEquals(listOf("text:caption:null"), arrangement.events)
    }

    @Test
    fun givenCaptionStoredButNotDelivered_whenSending_thenTheGifStillFollowsIt() = runTest {
        val failure = CoreFailure.Unknown(null)
        val arrangement = Arrangement(captionResult = MessageOperationResult.Failure(failure, messageId = "caption-id"))

        assertEquals(SendGifMessageResult.Success, arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null))

        assertEquals(listOf("text:caption:null", "gif"), arrangement.events)
    }

    @Test
    fun givenGifSchedulingFails_whenSending_thenFails() = runTest {
        val failure = CoreFailure.Unknown(null)
        val arrangement = Arrangement(gifResult = ScheduleNewAssetMessageResult.Failure.Generic(failure))

        assertIs<SendGifMessageResult.Failure>(arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null))
    }

    @Test
    fun givenCallerCancelledWhileTheCaptionSends_whenSending_thenTheGifStillFollows() = runTest {
        val captionSending = CompletableDeferred<Unit>()
        val captionDelivered = CompletableDeferred<MessageOperationResult>()
        val arrangement = Arrangement(
            scope = backgroundScope,
            sendCaption = {
                captionSending.complete(Unit)
                captionDelivered.await()
            },
        )
        val caller = launch { arrangement.useCase(CONVERSATION_ID, DOWNLOADED_GIF, "caption", null) }
        captionSending.await()

        caller.cancelAndJoin()
        captionDelivered.complete(MessageOperationResult.Success)
        arrangement.gifScheduled.await()

        assertEquals(listOf("text:caption:null", "gif"), arrangement.events)
    }

    private class Arrangement(
        scope: CoroutineScope? = null,
        private val sendCaption: (suspend () -> MessageOperationResult)? = null,
        fileSharing: FileSharingStatus.Value = FileSharingStatus.Value.EnabledAll,
        gifAllowed: Boolean = true,
        private val captionResult: MessageOperationResult = MessageOperationResult.Success,
        gifResult: ScheduleNewAssetMessageResult = ScheduleNewAssetMessageResult.Success("gif-id"),
    ) {
        val events = mutableListOf<String>()
        val sentAssets = mutableListOf<AssetUploadParams>()
        val gifScheduled = CompletableDeferred<Unit>()
        private val sendAssetMessage = mock<ScheduleNewAssetMessageUseCase>()
        private val observeFileSharingStatus = mock<ObserveFileSharingStatusUseCase>()
        private val validateAssetFileType = mock<ValidateAssetFileTypeUseCase>()

        init {
            every { observeFileSharingStatus() } returns flowOf(FileSharingStatus(fileSharing, false))
            every { validateAssetFileType(any(), any(), any()) } returns gifAllowed
            everySuspend { sendAssetMessage(any()) } calls { invocation ->
                events += "gif"
                sentAssets += invocation.args[0] as AssetUploadParams
                gifScheduled.complete(Unit)
                gifResult
            }
        }

        val useCase = SendGifMessageUseCaseImpl(
            sendText = { _, text, quotedMessageId ->
                events += "text:$text:$quotedMessageId"
                sendCaption?.invoke() ?: captionResult
            },
            sendAssetMessage = sendAssetMessage,
            observeFileSharingStatus = observeFileSharingStatus,
            validateAssetFileType = validateAssetFileType,
            scope = scope ?: CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation", "wire.test")
        val DOWNLOADED_GIF = DownloadedGif(
            gif = Gif(
                id = "abc",
                title = "Happy Friday",
                preview = GifRendition("https://media.giphy.com/abc/200w_d.gif", 200, 113, 100L),
                original = GifRendition("https://media.giphy.com/abc/downsized.gif", 480, 270, 1_000L),
            ),
            path = "/cache/giphy-1.gif".toPath(),
            sizeInBytes = 1_000L,
        )
    }
}
