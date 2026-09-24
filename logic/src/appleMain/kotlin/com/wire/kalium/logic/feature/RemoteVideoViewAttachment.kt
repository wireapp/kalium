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

/** A native remote view remains attached until its owner calls [detach]. Detaching twice is safe. */
public expect class RemoteVideoViewAttachment {
    public suspend fun detach()
}

/**
 * Attach an iOS AVSVideoView to one remote client's stream. The caller owns the view lifetime.
 * Attaching another view replaces the previous attachment; detach the returned lease when the view disappears.
 */
public expect suspend fun UserSessionScope.attachRemoteVideoView(
    participantId: QualifiedID,
    clientId: String,
    view: PlatformView,
): RemoteVideoViewAttachment
