/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.PlatformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual class LocalVideoPreviewAttachment internal constructor(
    private val owner: LocalVideoPreviewAttachmentOwner,
    internal val view: PlatformView,
) {
    public actual suspend fun startCapture(): Unit = withContext(Dispatchers.Main) {
        owner.startCapture(this@LocalVideoPreviewAttachment)
    }

    public actual suspend fun stopCapture(): Unit = withContext(Dispatchers.Main) {
        owner.stopCapture(this@LocalVideoPreviewAttachment)
    }

    public actual suspend fun detach(): Unit = withContext(Dispatchers.Main) {
        owner.detach(this@LocalVideoPreviewAttachment)
    }
}

internal class LocalVideoPreviewAttachmentOwner(
    private val attachNative: (PlatformView) -> Boolean,
    private val detachNative: (PlatformView) -> Boolean,
    private val startNativeCapture: () -> Unit,
    private val stopNativeCapture: () -> Unit,
) {
    private var current: LocalVideoPreviewAttachment? = null
    private var isCapturing = false

    fun attach(view: PlatformView): LocalVideoPreviewAttachment {
        current?.let { previous ->
            if (previous.view === view) return previous
            detach(previous)
        }
        check(attachNative(view)) { "AVS could not attach the local video preview" }
        return LocalVideoPreviewAttachment(this, view).also { current = it }
    }

    fun startCapture(attachment: LocalVideoPreviewAttachment) {
        if (current !== attachment || isCapturing) return
        startNativeCapture()
        isCapturing = true
    }

    fun stopCapture(attachment: LocalVideoPreviewAttachment) {
        if (current !== attachment || !isCapturing) return
        stopNativeCapture()
        isCapturing = false
    }

    fun detach(attachment: LocalVideoPreviewAttachment) {
        if (current !== attachment) return
        stopCapture(attachment)
        check(detachNative(attachment.view)) { "AVS could not detach the local video preview" }
        current = null
    }
}

public actual suspend fun UserSessionScope.attachLocalVideoPreview(view: PlatformView): LocalVideoPreviewAttachment =
    globalCallManager.flowManagerService.attachLocalVideoPreview(view)
