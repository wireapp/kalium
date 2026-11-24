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

@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.experimental.ExperimentalObjCRefinement::class,
)

package com.wire.kalium.share

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiving.appleAccountFileKey
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingOptions
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.posix.rename
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An account's outbox in the App Group: shares the extension hands to the app while the app owns the account (ADR 13).
 * The extension publishes an entry by renaming it from `staging/` into `ready/`, and the app claims it by renaming it
 * into `claimed/`; a rename within one file system is atomic. Nothing comes back: once the app has a share, the app
 * sends it or tells the user. The host rings the app with a Darwin notification; these files say what is there.
 */
// Both sides of the file protocol stay in one place.
@Suppress("TooManyFunctions")
internal class ShareOutbox(rootPath: String, accountId: UserId) {
    private val fileSystem = FileSystem.SYSTEM
    private val root = rootPath.trimEnd('/').toPath()
    private val outboxes = root / DIRECTORY
    private val directory = outboxes / appleAccountFileKey(accountId)

    /** Where earlier builds kept draft uploads, for every account. Nothing reads them any more. */
    private val oldDrafts = root / "share-drafts"
    private val staging = directory / "staging"
    private val ready = directory / "ready"
    private val claimed = directory / "claimed"
    private val conversationsRequest = directory / "conversations-request"
    private val conversations = directory / "conversations.json"

    /** Copies [files] next to [request], publishes the entry and returns its ID. IDs sort oldest first. */
    fun handOff(request: OutboxRequest, files: List<String>): String {
        @OptIn(ExperimentalUuidApi::class)
        val id = "${Clock.System.now().toEpochMilliseconds().toString().padStart(ID_TIME_DIGITS, '0')}-${Uuid.random()}"
        val entry = staging / id
        try {
            create(entry)
            files.forEachIndexed { index, path -> copy(path, entry / index.toString()) }
            fileSystem.write(entry / REQUEST) { writeUtf8(json.encodeToString(request)) }
            create(ready)
            if (rename(entry.toString(), (ready / id).toString()) != 0) throw IOException("Cannot publish the share")
        } catch (failure: IOException) {
            fileSystem.deleteRecursively(entry)
            throw failure
        }
        return id
    }

    /** Whether the app has not taken entry [id] yet. */
    fun isWaiting(id: String): Boolean = fileSystem.exists(ready / id)

    /** Whether entry [id] was handed off within the last day: its ID starts with that time. */
    fun isRecent(id: String): Boolean {
        val handedOff = id.substringBefore('-').toLongOrNull() ?: return false
        return Clock.System.now().toEpochMilliseconds() - handedOff < RETRY_FOR.inWholeMilliseconds
    }

    /** Asks the app for the account's conversations; an earlier answer is dropped. */
    fun askForConversations() {
        fileSystem.delete(conversations)
        create(directory)
        fileSystem.write(conversationsRequest) {}
    }

    /** The app's answer to [askForConversations], once. */
    fun takeConversations(): SharePicker? = take(conversations)?.let {
        try {
            json.decodeFromString<SharePicker>(it)
        } catch (_: SerializationException) {
            null
        }
    }

    /** Answers [askForConversations] with [picker]. Conversation names are readable only while the device is unlocked. */
    suspend fun answerIfAsked(picker: suspend () -> SharePicker) {
        if (!fileSystem.exists(conversationsRequest)) return
        writeWhileUnlocked(conversations, json.encodeToString(picker()))
        fileSystem.delete(conversationsRequest)
    }

    /** Claims every published entry, and returns every claimed one oldest first, with any an import left unfinished. */
    fun claim(): List<String> {
        fileSystem.listOrNull(ready)?.forEach { entry ->
            create(claimed)
            rename(entry.toString(), (claimed / entry.name).toString())
        }
        return fileSystem.listOrNull(claimed).orEmpty().map { it.name }.sorted()
    }

    /** Null when the request cannot be read, which no later import would change. */
    fun request(id: String): OutboxRequest? = try {
        json.decodeFromString<OutboxRequest>(fileSystem.read(claimed / id / REQUEST) { readUtf8() })
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    /**
     * A copy of a claimed file for the app's storage, which deletes what it takes before it stores the message. The
     * claimed file stays until the entry is finished, so an import interrupted in between can store it again.
     */
    fun importCopy(id: String, index: Int): String {
        val target = claimed / id / "$index.import"
        fileSystem.delete(target)
        copy((claimed / id / index.toString()).toString(), target)
        return target.toString()
    }

    /** Drops entry [id], including files the import did not take. */
    fun finish(id: String) = fileSystem.deleteRecursively(claimed / id)

    /**
     * Drops what its writer no longer needs at [now]: entries an interrupted extension left in `staging/`, and a
     * request for conversations, or its answer with every conversation's name, whose sheet stopped waiting long ago.
     */
    fun sweep(now: Instant = Clock.System.now()) {
        fun old(path: Path, age: Duration): Boolean {
            val modified = fileSystem.metadataOrNull(path)?.lastModifiedAtMillis
            return modified != null && modified < (now - age).toEpochMilliseconds()
        }
        fileSystem.listOrNull(staging)?.filter { old(it, SWEEP_AFTER) }?.forEach { fileSystem.deleteRecursively(it) }
        listOf(conversationsRequest, conversations).filter { old(it, LIST_SWEEP_AFTER) }.forEach { fileSystem.delete(it) }
        // What earlier builds left behind: results for the extension, and draft uploads.
        listOf(directory / "results", directory / "unwatched", oldDrafts).forEach { fileSystem.deleteRecursively(it) }
    }

    /** Drops every entry, result and answer of the account. */
    fun delete() {
        fileSystem.deleteRecursively(directory)
        fileSystem.deleteRecursively(oldDrafts)
    }

    /** Reads a file once: it is gone afterwards. */
    private fun take(path: Path): String? = try {
        val content = fileSystem.read(path) { readUtf8() }
        fileSystem.delete(path, mustExist = true)
        content
    } catch (_: IOException) {
        null
    }

    /** Shares stay on this device: the outboxes are excluded from backups. */
    private fun create(path: Path) {
        val excluded = fileSystem.exists(outboxes)
        fileSystem.createDirectories(path)
        if (!excluded) NSURL.fileURLWithPath(outboxes.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    }

    private companion object {
        private const val DIRECTORY = "share-outbox"
        private const val REQUEST = "request.json"
        private const val ID_TIME_DIGITS = 13
        private val SWEEP_AFTER = 1.hours

        /** A sheet waits seconds for its list of conversations. */
        private val LIST_SWEEP_AFTER = 1.minutes
        private val RETRY_FOR = 24.hours
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Drops an account's share outbox when it logs out (ADR 13): shares not imported yet and conversation lists.
 * Each holds plaintext outside the account's directory, which logging out deletes.
 */
@InternalKaliumApi
@HiddenFromObjC
public fun deleteShareOutbox(rootPath: String, accountId: UserId) {
    try {
        ShareOutbox(rootPath, accountId).delete()
    } catch (failure: IOException) {
        // Logging out must go on.
        kaliumLogger.w("Unable to delete the share outbox", failure)
    }
}

/** A clone where the file system allows it, so large videos copy instantly. */
private fun copy(source: String, target: Path) {
    if (!NSFileManager.defaultManager.copyItemAtPath(source, target.toString(), null)) throw IOException("Cannot copy a shared file")
}

/** Replaces [path] at once, so the other side never reads half of it, and keeps it unreadable while the device is locked. */
private fun writeWhileUnlocked(path: Path, text: String) {
    val data = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding) ?: throw IOException("Cannot encode")
    if (!data.writeToFile(path.toString(), NSDataWritingAtomic or readableWhileUnlocked, null)) {
        throw IOException("Cannot write to the share outbox")
    }
}

/** The file protection that keeps a file unreadable while the device is locked. */
internal expect val readableWhileUnlocked: NSDataWritingOptions

/** A handed-off share. Its files are stored next to it, by index. */
@Serializable
internal data class OutboxRequest(
    @SerialName("conversationValue") val conversationValue: String,
    @SerialName("conversationDomain") val conversationDomain: String,
    /** Each file becomes its own message, stored before the text, as a caption follows what it describes. */
    @SerialName("files") val files: List<OutboxFile>,
    @SerialName("text") val text: String,
    @SerialName("textMessageId") val textMessageId: String,
    /** Set on draft work earlier builds queued. It was never a share to send, so it is dropped. */
    @SerialName("draftAction") val draftAction: String? = null,
)

/** Each item keeps the message ID the extension gave it, so importing it again stores nothing twice. */
@Serializable
internal data class OutboxFile(
    @SerialName("messageId") val messageId: String,
    @SerialName("name") val name: String,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("sizeInBytes") val sizeInBytes: Long,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("durationMs") val durationMs: Long? = null,
)
