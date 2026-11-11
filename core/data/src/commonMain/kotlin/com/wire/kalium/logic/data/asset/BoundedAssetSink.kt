/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.asset

import okio.Buffer
import okio.IOException
import okio.Sink

/** Stops streaming transfers before an oversized write reaches disk. */
class BoundedAssetSink(private val delegate: Sink, private val maxBytes: Long) : Sink by delegate {
    private var bytesWritten = 0L
    var limitExceeded: Boolean = false
        private set

    override fun write(source: Buffer, byteCount: Long) {
        if (limitExceeded || byteCount > maxBytes - bytesWritten) {
            limitExceeded = true
            throw IOException("Asset preview exceeds size limit")
        }
        delegate.write(source, byteCount)
        bytesWritten += byteCount
    }
}
