/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiving.appleAccountFileKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Extension drafts are separate from the conversation composer's draft table. Only the account owner writes results. */
internal class ShareDraftStore(rootPath: String, userId: UserId) {
    private val fs = FileSystem.SYSTEM
    private val directory = rootPath.toPath() / "share-drafts" / appleAccountFileKey(userId)
    private val json = Json { ignoreUnknownKeys = true }

    fun create(conversationId: ConversationId, file: ShareFile): String {
        val id = Uuid.random().toString()
        fs.createDirectories(directory)
        write(id, ShareDraftRecord(conversationId.value, conversationId.domain, file))
        return id
    }

    fun read(id: String): ShareDraftRecord? {
        requireId(id)
        val path = directory / id
        if (!fs.exists(path)) return null
        return try {
            json.decodeFromString<ShareDraftRecord>(fs.read(path) { readUtf8() })
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }

    fun write(id: String, record: ShareDraftRecord) {
        requireId(id)
        fs.createDirectories(directory)
        writeShareOutboxFile(directory / id, json.encodeToString(record), whileUnlocked = true)
    }

    fun cancel(id: String) {
        requireId(id)
        fs.createDirectories(directory)
        fs.write(directory / "$id.cancelled") {}
    }

    fun isCancelled(id: String): Boolean {
        requireId(id)
        return fs.exists(directory / "$id.cancelled")
    }

    fun consume(ids: List<String>) {
        ids.forEach { id ->
            requireId(id)
            fs.delete(directory / id)
            fs.delete(directory / "$id.cancelled")
        }
    }

    @Suppress("ReturnCount")
    fun resolve(ids: List<String>, conversationId: ConversationId): List<ShareDraftRecord>? {
        if (ids.isEmpty() || ids.distinct().size != ids.size) return null
        return ids.map { id ->
            val record = read(id) ?: return null
            if (isCancelled(id) || record.conversationId != conversationId || record.asset == null) return null
            record
        }
    }

    private fun requireId(id: String) { require(Uuid.parseOrNull(id) != null) }
}

@Serializable
internal data class ShareDraftRecord(
    @SerialName("conversationValue") val conversationValue: String,
    @SerialName("conversationDomain") val conversationDomain: String,
    @SerialName("file") val file: ShareFile,
    @SerialName("asset") val asset: SharedDraftAsset? = null,
    @SerialName("progress") val progress: Double = 0.0,
    @SerialName("failure") val failure: String? = null,
    @SerialName("createdAt") val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    val conversationId: ConversationId get() = ConversationId(conversationValue, conversationDomain)
}

@Serializable
internal data class SharedDraftAsset(
    @SerialName("id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("path") val path: String,
    @SerialName("cellName") val cellName: String
) {
    fun content(file: ShareFile): CellAssetContent = CellAssetContent(
        id = id,
        versionId = version,
        mimeType = file.mimeType,
        assetPath = path,
        assetSize = file.sizeInBytes,
        metadata = file.cellFile().metadata,
        transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
    )
}

/** Polling cancellation also works when the extension and uploader are different processes. */
@Suppress("CyclomaticComplexMethod") // Keep the cancellation/ownership transfer in one try/finally.
internal suspend fun prepareShareDraft(
    store: ShareDraftStore,
    id: String,
    upload: suspend (ShareDraftRecord, (Long, Long) -> Unit) -> Either<String, SharedDraftAsset>,
    discard: suspend (ShareDraftRecord) -> Unit,
): Unit = coroutineScope {
    val record = store.read(id) ?: return@coroutineScope
    if (record.asset != null || store.isCancelled(id)) return@coroutineScope
    kaliumLogger.i("Cells share stage=DRAFT_PREPARE_START")
    var uploaded: ShareDraftRecord? = null
    var retained = false
    val work = async {
        withTimeoutOrNull(5.minutes) {
            upload(record) { bytes, total ->
                val fraction = if (total > 0) bytes.toDouble() / total else 0.0
                store.write(id, record.copy(progress = fraction.coerceIn(0.0, 1.0), failure = null))
            }.also { result ->
                if (result is Either.Right) uploaded = record.copy(asset = result.value, progress = 1.0, failure = null)
            }
        } ?: Either.Left("UPLOAD_TIMEOUT")
    }
    val cancelled = async {
        while (!store.isCancelled(id)) delay(DRAFT_CANCELLATION_POLL_MS)
    }
    try {
        select {
            work.onAwait { result ->
                if (!store.isCancelled(id)) {
                    store.write(
                        id,
                        when (result) {
                            is Either.Left -> record.copy(failure = result.value)
                            is Either.Right -> requireNotNull(uploaded)
                        }
                    )
                    retained = true
                    kaliumLogger.i("Cells share stage=DRAFT_PREPARE_COMPLETE success=${result is Either.Right}")
                }
            }
            cancelled.onAwait { }
        }
    } catch (failure: CancellationException) {
        throw failure
    } finally {
        withContext(NonCancellable) {
            work.cancel()
            cancelled.cancel()
            work.join()
            cancelled.join()
            if (!retained) {
                uploaded?.let { discard(it) }
                kaliumLogger.i("Cells share stage=DRAFT_DISCARD")
                if (store.isCancelled(id)) store.consume(listOf(id))
                else store.write(id, record.copy(failure = "UPLOAD_INTERRUPTED"))
            }
        }
    }
}

private const val DRAFT_CANCELLATION_POLL_MS = 100L
