/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.data.FileDownloader
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.BufferedSink

/**
 * Download a cell version from the wire cell server via presigned url.
 */
public interface DownloadCellVersionUseCase {
    public suspend operator fun invoke(
        bufferedSink: BufferedSink,
        preSignedUrl: String,
        onProgressUpdate: (Long) -> Unit,
        onCompleted: () -> Unit,
    ): Either<CoreFailure, Unit>
}

internal class DownloadCellVersionUseCaseImpl internal constructor(
    private val fileDownloader: FileDownloader,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DownloadCellVersionUseCase {

    override suspend fun invoke(
        bufferedSink: BufferedSink,
        preSignedUrl: String,
        onProgressUpdate: (Long) -> Unit,
        onCompleted: () -> Unit,
    ) = withContext(dispatchers.io) {
        fileDownloader.downloadViaPresignedUrl(
            presignedUrl = preSignedUrl,
            outFileSink = bufferedSink,
            onProgressUpdate = onProgressUpdate,
            onCompleted = onCompleted
        )
    }
}
