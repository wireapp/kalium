/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.message.linkpreview

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Receiver limits are independent of sender-provided sizes and shared by all resolver instances. */
object LinkPreviewImageLimits {
    const val MAX_SIZE_IN_BYTES: Long = 5 * 1024 * 1024

    // AES-CBC adds a 16-byte IV and at most one full padding block.
    const val MAX_ENCRYPTED_SIZE_IN_BYTES: Long = MAX_SIZE_IN_BYTES + 32

    // Generation produces a preview only for the first eligible link.
    const val MAX_PREVIEWS_PER_MESSAGE: Int = 1
    const val MAX_CONCURRENT_DOWNLOADS: Int = 2

    private val downloads = Semaphore(MAX_CONCURRENT_DOWNLOADS)

    suspend fun <T> withDownloadPermit(block: suspend () -> T): T = downloads.withPermit { block() }
}
