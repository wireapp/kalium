/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentDraftUploadResult
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

internal suspend fun CellUploadManager.awaitDraftTransfer(uuid: String): CellAttachmentDraftUploadResult = coroutineScope {
    val events = observeUpload(uuid)
    // Subscribe before inspecting snapshots: the event flow has no replay.
    val terminal = events?.let { source ->
        async(start = CoroutineStart.UNDISPATCHED) { source.first { it !is CellUploadEvent.UploadProgress } }
    }
    val current = getUploadInfo(uuid)
    val recorded = consumeUploadOutcome(uuid)
    val outcome = when {
        recorded != null -> recorded
        current?.uploadFailed == true -> CellUploadEvent.UploadError
        current == null || terminal == null -> null
        else -> terminal.await()
    }
    terminal?.cancelAndJoin()
    when (outcome) {
        CellUploadEvent.UploadCompleted -> CellAttachmentDraftUploadResult.Completed
        CellUploadEvent.UploadCancelled -> CellAttachmentDraftUploadResult.Cancelled
        else -> CellAttachmentDraftUploadResult.Failed
    }
}
