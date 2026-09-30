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

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.util.PlatformView

/**
 * Owns one native remote view attachment. [detach] releases only this attachment and runs on the main dispatcher.
 * Repeated detach, or detach after the same view has been rebound, does nothing.
 * A failed native detach throws and leaves the attachment owned so the caller can retry.
 */
public expect class RemoteVideoViewAttachment {
    public suspend fun detach()
}

/**
 * Attach an iOS AVSVideoView to one remote client's stream. The caller owns the view lifetime.
 * Different native views remain attached independently, including views of the same stream.
 * Reattaching the same native view and stream returns its existing lease, even through another [PlatformView] wrapper.
 * Rebinding that view to another stream detaches its previous attachment and invalidates the old lease.
 * If that detach fails, the previous attachment remains owned and the new stream is not attached.
 * If the new attach fails after detach succeeds, that view remains detached. Other views are unaffected.
 * Operations run on the main dispatcher. The caller must detach each returned lease when its view disappears.
 */
public expect suspend fun UserSessionScope.attachRemoteVideoView(
    participantId: QualifiedID,
    clientId: String,
    view: PlatformView,
): RemoteVideoViewAttachment
