/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.backup.dump

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.data.toProtoModel
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.BackupHeader
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HashData
import com.wire.backup.filesystem.BackupPage
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.ingest.MPBackupMapper
import com.wire.backup.ingest.close
import com.wire.kalium.protobuf.backup.BackupData
import com.wire.kalium.protobuf.backup.BackupInfo
import com.wire.kalium.protobuf.backup.ExportUser
import com.wire.kalium.protobuf.backup.ExportedConversation
import com.wire.kalium.protobuf.backup.ExportedMessage
import kotlinx.coroutines.Deferred
import kotlinx.datetime.Clock
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import pbandk.encodeToByteArray
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform [BackupData] format.
 */
@JsExport
public abstract class CommonMPBackupExporter(
    private val selfUserId: BackupQualifiedId
) {
    private val mapper = MPBackupMapper()
    private val usersChunk = mutableListOf<ExportUser>()
    private val conversationsChunk = mutableListOf<ExportedConversation>()
    private val messagesChunk = mutableListOf<ExportedMessage>()
    private var persistedUserChunks = 0
    private var persistedConversationsChunks = 0
    private var persistedMessagesChunks = 0

    private val backupInfo by lazy {
        BackupInfo(
            platform = "Common",
            version = "1.0",
            userId = selfUserId.toProtoModel(),
            creationTime = Clock.System.now().toEpochMilliseconds(),
            clientId = "lol"
        )
    }

    @JsName("addUser")
    public fun add(user: BackupUser) {
        usersChunk.add(mapper.mapUserToProtobuf(user))
        if (usersChunk.size > ITEMS_CHUNK_SIZE) {
            flushUsers()
        }
    }

    private fun flushUsers() {
        if (usersChunk.isEmpty()) return
        val backupData = BackupData(backupInfo, users = usersChunk)
        storage.persistEntry(
            BackupPage(
                name = BackupPage.USERS_PREFIX + persistedUserChunks + BackupPage.PAGE_SUFFIX,
                data = backupData.asSource()
            )
        )
        persistedUserChunks++
        usersChunk.clear()
    }

    @JsName("addConversation")
    public fun add(conversation: BackupConversation) {
        conversationsChunk.add(mapper.mapConversationToProtobuf(conversation))
        if (conversationsChunk.size > ITEMS_CHUNK_SIZE) {
            flushConversations()
        }
    }

    private fun flushConversations() {
        if (conversationsChunk.isEmpty()) return
        val backupData = BackupData(backupInfo, conversations = conversationsChunk)
        storage.persistEntry(
            BackupPage(
                name = BackupPage.CONVERSATIONS_PREFIX + persistedConversationsChunks + BackupPage.PAGE_SUFFIX,
                data = backupData.asSource()
            )
        )
        persistedConversationsChunks++
        conversationsChunk.clear()
    }

    @JsName("addMessage")
    public fun add(message: BackupMessage) {
        messagesChunk.add(mapper.mapMessageToProtobuf(message))
        if (messagesChunk.size > ITEMS_CHUNK_SIZE) {
            flushMessages()
        }
    }

    private fun flushMessages() {
        if (messagesChunk.isEmpty()) return
        val backupData = BackupData(backupInfo, messages = messagesChunk)
        storage.persistEntry(
            BackupPage(
                name = BackupPage.MESSAGES_PREFIX + persistedMessagesChunks + BackupPage.PAGE_SUFFIX,
                data = backupData.asSource()
            )
        )
        persistedMessagesChunks++
        messagesChunk.clear()
    }

    private fun flushAll() {
        flushUsers()
        flushConversations()
        flushMessages()
    }

    private fun BackupData.asSource(): Source {
        val buffer = Buffer()
        return buffer.write(this.encodeToByteArray())
    }

    /**
     * Finalize the export and write the data to the given output.
     * This method should be called after all the data was added.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun finalize(password: String, output: Sink): ExportResult {
        flushAll()
        return try {
            val entries = storage.listEntries()
            try {
                zipEntries(entries).await().use { source ->
                    writeBackupArtifact(output, password, source)
                }
            } finally {
                entries.close()
            }
        } catch (t: Throwable) {
            ExportResult.Failure.ZipError(t.message ?: "Unknown ZIP error.")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun writeBackupArtifact(output: Sink, password: String, zippedData: Source): ExportResult = try {
        val isEncrypted = password.isNotBlank()
        val header = BackupHeader(
            BackupHeaderSerializer.Default.CURRENT_HEADER_VERSION,
            isEncrypted,
            HashData.defaultFromUserId(selfUserId)
        )
        val headerBytes = BackupHeaderSerializer.Default.headerToBytes(header)
        output.buffer().use { bufferedOutput ->
            bufferedOutput.write(headerBytes)
            bufferedOutput.flush()
            if (!isEncrypted) {
                // We should skip the encryption headers, leaving empty/zeroed bytes
                val skip = ByteArray(EncryptedStream.XCHACHA_20_POLY_1305_HEADER_LENGTH) { 0x00 }
                bufferedOutput.write(skip)
                bufferedOutput.writeAll(zippedData)
            } else {
                EncryptedStream.encrypt(
                    zippedData,
                    bufferedOutput,
                    XChaChaPoly1305AuthenticationData(
                        password,
                        header.hashData.salt,
                        headerBytes.toUByteArray(),
                        header.hashData.operationsLimit,
                        header.hashData.hashingMemoryLimit
                    )
                )
            }
            bufferedOutput
        }
        ExportResult.Success
    } catch (t: Throwable) {
        ExportResult.Failure.IOError(t.message ?: "Unknown IO error.")
    }

    internal abstract val storage: BackupPageStorage

    internal abstract fun zipEntries(data: List<BackupPage>): Deferred<Source>

    private companion object {
        /**
         * Amount of items (conversations or messages) to be put into a single page / entry
         */
        const val ITEMS_CHUNK_SIZE = 1_000
    }
}

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform [BackupData] format.
 */
public expect class MPBackupExporter : CommonMPBackupExporter
