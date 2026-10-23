/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.logic.data.giphy.GiphyRepository
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.message.SendTextMessageUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import kotlinx.coroutines.CoroutineScope

/**
 * GIF search and sending through the backend's Giphy proxy.
 * Search requests go through the Wire backend; GIF files load directly from Giphy's CDN.
 */
public class GiphyScope internal constructor(
    private val giphyRepository: GiphyRepository,
    private val sendTextMessage: SendTextMessageUseCase,
    private val sendAssetMessage: ScheduleNewAssetMessageUseCase,
    private val observeFileSharingStatus: ObserveFileSharingStatusUseCase,
    private val validateAssetFileType: ValidateAssetFileTypeUseCase,
    private val scope: CoroutineScope,
) {
    public val searchGifs: SearchGifsUseCase get() = SearchGifsUseCaseImpl(giphyRepository)

    public val getTrendingGifs: GetTrendingGifsUseCase get() = GetTrendingGifsUseCaseImpl(giphyRepository)

    public val getGifSuggestions: GetGifSuggestionsUseCase get() = GetGifSuggestionsUseCaseImpl(giphyRepository)

    public val downloadGif: DownloadGifUseCase get() = DownloadGifUseCaseImpl(giphyRepository)

    public val sendGifMessage: SendGifMessageUseCase
        get() = SendGifMessageUseCaseImpl(
            sendText = { conversationId, text, quotedMessageId ->
                sendTextMessage(conversationId, text, quotedMessageId = quotedMessageId)
            },
            sendAssetMessage = sendAssetMessage,
            observeFileSharingStatus = observeFileSharingStatus,
            validateAssetFileType = validateAssetFileType,
            scope = scope,
        )
}
