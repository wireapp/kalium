/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.logic.feature.giphy

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.giphy.DownloadedGif
import com.wire.kalium.logic.data.giphy.GifPage

public sealed interface GetGifsResult {
    public data class Success(val page: GifPage) : GetGifsResult

    /** The Wire backend does not run the Giphy proxy. Retrying does not help. */
    public data object Unavailable : GetGifsResult

    public data class Failure(val coreFailure: CoreFailure) : GetGifsResult
}

public sealed interface DownloadGifResult {
    public data class Success(val downloadedGif: DownloadedGif) : DownloadGifResult
    public data class Failure(val coreFailure: CoreFailure) : DownloadGifResult
}

public sealed interface SendGifMessageResult {
    public data object Success : SendGifMessageResult

    /** The team disabled file sharing. Nothing was sent. */
    public data object DisabledByTeam : SendGifMessageResult

    /** The team's file sharing settings do not allow GIFs. Nothing was sent. */
    public data object RestrictedFileType : SendGifMessageResult

    public data class Failure(val coreFailure: CoreFailure) : SendGifMessageResult
}
