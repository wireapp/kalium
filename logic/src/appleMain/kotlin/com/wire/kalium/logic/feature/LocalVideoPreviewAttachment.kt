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

/**
 * A local preview stays attached until [detach]. Capture starts only after [startCapture].
 * Capture and call video-send state are separate; [detach] stops capture before removing the view.
 */
public expect class LocalVideoPreviewAttachment {
    public suspend fun startCapture()
    public suspend fun stopCapture()
    public suspend fun detach()
}

/**
 * Attach one plain native view as the local camera preview. The caller owns its lifetime.
 * Do not pass AVSVideoPreview, which attaches itself when added to a window.
 */
public expect suspend fun UserSessionScope.attachLocalVideoPreview(view: PlatformView): LocalVideoPreviewAttachment
