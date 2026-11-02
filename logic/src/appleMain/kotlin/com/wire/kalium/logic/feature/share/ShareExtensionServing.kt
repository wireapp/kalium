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
import com.wire.kalium.cells.CellsScope
import com.wire.kalium.cells.CellSharedFile
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.share.metadata
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.feature.asset.upload.AssetUploadParams
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.share.ShareOutboxFile
import com.wire.kalium.share.ShareOutboxSending
import com.wire.kalium.share.ShareOutboxServer
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

private val turns = ConcurrentMutableMap<UserId, Mutex>()

/**
 * Answers the share extension while the app owns this account, live or not (ADR 13). It lists the account's
 * conversations when the extension asks, and stores handed-off shares as the composer would: now, and whenever
 * [doorbell] rings. Runs until cancelled, which must happen before the app releases the account. Servers of one
 * account take turns. A share it refuses after the extension stopped waiting goes to [refused], for the app to tell
 * the user.
 */
public suspend fun UserSessionScope.serveShareExtension(
    doorbell: Flow<Unit>,
    refused: suspend (conversationId: ConversationId, conversationName: String?, reason: String) -> Unit,
) {
    turns.computeIfAbsent(userId) { Mutex() }.withLock {
        ShareOutboxServer(
            rootPath = rootPathsProvider.rootPath,
            selfUserId = userId,
            database = userStorage.database,
            restriction = kaliumConfigs.fileRestrictionState.value,
            sending = ComposerSending(
                messages = lazy { messages },
                syncStateObserver = syncStateObserver.value,
                cells = lazy { cells },
                conversations = conversations.conversationRepository,
            ),
            refused = refused,
        ).serve(doorbell)
    }
}

/** Stores handed-off items with the composer's use cases. A stored message is the app's, sent or not. */
internal class ComposerSending(
    private val messages: Lazy<MessageScope>,
    private val syncStateObserver: SyncStateObserver,
    private val cells: Lazy<CellsScope>,
    private val conversations: ConversationRepository,
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
            is ScheduleNewAssetMessageResult.Failure.Generic -> ShareOutboxSending.FAILED
        }
    }

    @Suppress("ReturnCount")
    override suspend fun cells(
        conversationId: ConversationId,
        files: List<ShareOutboxFile>,
        text: String,
        messageId: String,
    ): String? {
        val conversation = conversations.observeConversationDetailsById(conversationId).first().getOrNull()
        val cellName = (conversation as? ConversationDetails.Group)?.wireCell ?: return ShareOutboxSending.UNAVAILABLE
        if (cells.value.getCellConfig()?.backendUrl.isNullOrBlank()) return ShareOutboxSending.UNAVAILABLE
        val prepared = files.map { file ->
            CellSharedFile(file.path.toPath(), file.name, file.mimeType, file.sizeInBytes, file.metadata())
        }
        return when (val uploaded = cells.value.fileSharing.upload(cellName, prepared)) {
            is Either.Left -> ShareOutboxSending.UPLOAD_FAILED
            is Either.Right -> {
                var stored = false
                try {
                    // A cancellation must not lose the ownership transfer after the message was persisted.
                    withContext(NonCancellable) {
                        stored = messages.value.sendMultipartMessage.storeShared(messageId, conversationId, text, uploaded.value)
                    }
                    if (stored) null else ShareOutboxSending.FAILED
                } finally {
                    if (!stored) cells.value.fileSharing.discard(uploaded.value)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    override suspend fun prepareCells(
        conversationId: ConversationId,
        files: List<ShareOutboxFile>,
        progress: (Long, Long) -> Unit,
    ): Either<String, List<CellAssetContent>> {
        val conversation = conversations.observeConversationDetailsById(conversationId).first().getOrNull()
        val cellName = (conversation as? ConversationDetails.Group)?.wireCell
            ?: return Either.Left(ShareOutboxSending.UNAVAILABLE)
        if (cells.value.getCellConfig()?.backendUrl.isNullOrBlank()) return Either.Left(ShareOutboxSending.UNAVAILABLE)
        val prepared = files.map { CellSharedFile(it.path.toPath(), it.name, it.mimeType, it.sizeInBytes, it.metadata()) }
        return when (val result = cells.value.fileSharing.upload(cellName, prepared, progress)) {
            is Either.Left -> Either.Left(ShareOutboxSending.UPLOAD_FAILED)
            is Either.Right -> result
        }
    }

    override suspend fun discardCells(attachments: List<CellAssetContent>) {
        cells.value.fileSharing.discard(attachments)
    }

    override suspend fun storeCells(
        conversationId: ConversationId,
        attachments: List<CellAssetContent>,
        text: String,
        messageId: String,
    ): String? = withContext(NonCancellable) {
        if (messages.value.sendMultipartMessage.storeShared(messageId, conversationId, text, attachments)) null
        else ShareOutboxSending.FAILED
    }

    override suspend fun text(conversationId: ConversationId, text: String, messageId: String): String? =
        if (messages.value.sendTextMessage.store(messageId, conversationId, text)) null else ShareOutboxSending.FAILED
}
