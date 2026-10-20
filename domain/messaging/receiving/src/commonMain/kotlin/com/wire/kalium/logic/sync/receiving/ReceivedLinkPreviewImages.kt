/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.hasRemoteAssetData
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import okio.Buffer
import okio.ForwardingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.use
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Optional image work stays within the owned NSE lifetime; failures leave the text usable. */
internal class ReceivedLinkPreviewImages(
    private val enabled: Boolean,
    private val assetApi: AssetApi,
    assetDirectory: String,
) {
    private val directory = assetDirectory.toPath().resolve("nse-previews")
    private val files = receivedPreviewFileSystem()
    private val created = mutableListOf<Path>()

    suspend fun resolve(data: MessageEffectDAOs, message: Message.Regular) {
        if (!enabled || files == null) return
        (message.content as? MessageContent.Text)?.linkPreviews.orEmpty().forEach { preview ->
            val image = preview.image ?: return@forEach
            if (image.assetDataPath != null || !image.hasRemoteAssetData()) return@forEach
            try {
                val key = checkNotNull(image.assetKey)
                val cached = data.assets.getAssetByKey(key).first()
                val path = cached?.dataPath?.toPath()?.takeIf { files.exists(it) } ?: download(data.assets, image)
                data.messages.updateLinkPreviewImageLocalPath(
                    message.conversationId.toDao(),
                    message.id,
                    preview.urlOffset,
                    path.toString()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                kaliumLogger.w("Notification preview image is unavailable")
            }
        }
    }

    /** Recover only unreferenced files in our own directory, including an interrupted file/SQL gap. */
    suspend fun cleanupAbandoned(assets: AssetDAO) {
        if (files == null || !files.exists(directory)) return
        cleanup(assets, files.list(directory))
    }

    /** Called after SQL has committed or rolled back, including prompt coroutine cancellation. */
    suspend fun cleanupCreated(assets: AssetDAO) = cleanup(assets, created.toList())

    private suspend fun cleanup(assets: AssetDAO, paths: List<Path>) {
        val files = files ?: return
        paths.forEach { path ->
            currentCoroutineContext().ensureActive()
            if (!assets.isFileReferenced(path.toString())) files.delete(path, mustExist = false)
        }
        created.clear()
    }

    private suspend fun download(assets: AssetDAO, image: LinkPreviewAsset): Path {
        val files = checkNotNull(files)
        // The native CBC primitive expects a complete key and IV. Reject malformed remote data
        // before it reaches that primitive. The app resolver also uses CBC for preview images.
        require(image.otrKey.size == KEY_SIZE && image.sha256Key.size == KEY_SIZE) { "Invalid preview encryption keys" }
        files.createDirectories(directory)
        val name = Uuid.random().toString()
        val encrypted = directory.resolve("$name.encrypted").also { created += it }
        val decoded = directory.resolve("$name.decoded").also { created += it }
        val persistent = directory.resolve(name).also { created += it }
        val key = checkNotNull(image.assetKey)
        files.sink(encrypted).use { sink ->
            wrapApiRequest { assetApi.downloadAsset(key, image.assetDomain, image.assetToken?.ifEmpty { null }, sink) }.orThrow()
        }
        val hash = cancellableSource(encrypted).use { calcFileSHA256(it) }
        currentCoroutineContext().ensureActive() // The shared hash helper converts exceptions to null.
        check(hash != null && hash.contentEquals(image.sha256Key)) { "Preview image hash mismatch" }
        val size = checkNotNull(files.metadata(encrypted).size)
        require(size >= CBC_BLOCK_SIZE * 2 && size % CBC_BLOCK_SIZE == 0L) { "Invalid preview CBC data" }
        val decodedSize = cancellableSource(encrypted).use { source ->
            files.sink(decoded).use { sink -> decryptFileWithAES256(source, sink, AES256Key(image.otrKey)) }
        }
        currentCoroutineContext().ensureActive()
        check(decodedSize > 0) { "Preview image is empty" }
        files.atomicMove(decoded, persistent)
        val asset = AssetEntity(
            key = key,
            domain = image.assetDomain,
            dataPath = persistent.toString(),
            dataSize = decodedSize,
            downloadedDate = Clock.System.now().toEpochMilliseconds(),
        )
        if (assets.getAssetByKey(key).first() == null) assets.insertAsset(asset) else assets.updateAsset(asset)
        return persistent
    }

    private suspend fun cancellableSource(path: Path): Source {
        val files = checkNotNull(files)
        val context = currentCoroutineContext()
        return object : ForwardingSource(files.source(path)) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                context.ensureActive()
                return super.read(sink, byteCount)
            }
        }
    }

    private companion object {
        const val KEY_SIZE = 32
        const val CBC_BLOCK_SIZE = 16
    }
}
