/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.feature.share

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.asset.upload.AssetUploadParams
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.share.ShareOutboxFile
import com.wire.kalium.share.ShareOutboxSending
import com.wire.kalium.share.ShareOutboxServer
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

private val turns = ConcurrentMutableMap<UserId, Mutex>()

/**
 * Answers the share extension while the app owns this account, live or not (ADR 13). It lists the account's
 * conversations when the extension asks, and stores handed-off shares as the composer would: now, and whenever
 * [doorbell] rings. Runs until cancelled, which must happen before the app releases the account. Servers of one
 * account take turns.
 */
public suspend fun UserSessionScope.serveShareExtension(doorbell: Flow<Unit>) {
    turns.computeIfAbsent(userId) { Mutex() }.withLock {
        ShareOutboxServer(
            rootPath = rootPathsProvider.rootPath,
            selfUserId = userId,
            database = userStorage.database,
            restriction = kaliumConfigs.fileRestrictionState.value,
            sending = ComposerSending(lazy { messages }, syncStateObserver.value),
        ).serve(doorbell)
    }
}

/** Stores handed-off items with the composer's use cases. A stored message is the app's, sent or not. */
internal class ComposerSending(
    private val messages: Lazy<MessageScope>,
    private val syncStateObserver: SyncStateObserver,
) : ShareOutboxSending {

    override suspend fun awaitSynced() {
        // A failed sync ends the wait: offline, the import uses the stored rules, as the composer does.
        syncStateObserver.waitUntilLiveOrFailure()
    }

    override suspend fun file(conversationId: ConversationId, file: ShareOutboxFile, messageId: String): String? {
        val asset = AssetUploadParams(
            conversationId = conversationId,
            assetDataPath = file.path.toPath(),
            assetDataSize = file.sizeInBytes,
            assetName = file.name,
            assetMimeType = file.mimeType,
            assetWidth = file.width,
            assetHeight = file.height,
            // As the composer does, a video's duration goes here too.
            audioLengthInMs = file.durationMs ?: 0,
            audioNormalizedLoudness = null,
        )
        return when (messages.value.scheduleAssetMessage.invoke(asset, messageId)) {
            is ScheduleNewAssetMessageResult.Success -> null
            ScheduleNewAssetMessageResult.Failure.DisabledByTeam -> "FILE_SHARING_DISABLED"
            ScheduleNewAssetMessageResult.Failure.RestrictedFileType -> "FILE_TYPE_RESTRICTED"
            is ScheduleNewAssetMessageResult.Failure.Generic -> "FAILED"
        }
    }

    override suspend fun text(conversationId: ConversationId, text: String, messageId: String): String? =
        if (messages.value.sendTextMessage.store(messageId, conversationId, text)) null else "FAILED"
}
