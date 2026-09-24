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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual class RemoteVideoViewAttachment internal constructor(
    private val owner: RemoteVideoViewAttachmentOwner,
    internal val view: PlatformView,
    internal val userId: String,
    internal val clientId: String,
) {
    public actual suspend fun detach(): Unit = withContext(Dispatchers.Main) {
        owner.detach(this@RemoteVideoViewAttachment)
    }
}

internal class RemoteVideoViewAttachmentOwner(
    private val attachNative: (PlatformView, String, String) -> Boolean,
    private val detachNative: (PlatformView) -> Boolean,
) {
    private var current: RemoteVideoViewAttachment? = null

    fun attach(view: PlatformView, userId: String, clientId: String): RemoteVideoViewAttachment {
        current?.let { previous ->
            if (previous.view === view && previous.userId == userId && previous.clientId == clientId) return previous
            check(detachNative(previous.view)) { "AVS could not detach the previous remote video view" }
            current = null
        }
        check(attachNative(view, userId, clientId)) { "AVS could not attach the remote video view" }
        return RemoteVideoViewAttachment(this, view, userId, clientId).also { current = it }
    }

    fun detach(attachment: RemoteVideoViewAttachment) {
        if (current !== attachment) return
        check(detachNative(attachment.view)) { "AVS could not detach the remote video view" }
        current = null
    }
}

public actual suspend fun UserSessionScope.attachRemoteVideoView(
    participantId: QualifiedID,
    clientId: String,
    view: PlatformView,
): RemoteVideoViewAttachment {
    val avsUserId = federatedIdMapper.parseToFederatedId(participantId)
    return globalCallManager.flowManagerService.attachRemoteVideoView(view, avsUserId, clientId)
}
