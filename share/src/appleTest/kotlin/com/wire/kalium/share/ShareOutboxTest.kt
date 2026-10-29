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

package com.wire.kalium.share

import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ShareOutboxTest {

    @OptIn(ExperimentalUuidApi::class)
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-outbox-test-${Uuid.random()}"
    private val outbox = ShareOutbox(root.toString(), SELF)

    @AfterTest
    fun deleteRoot() = FileSystem.SYSTEM.deleteRecursively(root)

    @Test
    fun restartingAfterStoringACellsShareDoesNotUploadOrStoreItAgain() = runTest {
        withDatabase { database ->
            seed(database)
            conversation(database, "group", ConversationEntity.Type.GROUP, T2) { copy(wireCell = "cell") }
            val id = outbox.handOff(request(PHOTO, VIDEO), listOf(file("photo"), file("video")))
            val interrupted = RecordingSending(database, cancelOn = "t")
            assertFailsWith<CancellationException> { server(database, interrupted).importShares() }
            val restarted = RecordingSending(database)
            server(database, restarted).importShares()
            assertEquals(emptyList(), restarted.stored)
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), outbox.state(id))
        }
    }

    @Test
    fun cellsShareIsVisibleAndImportedAsOneBatchWithCaption() = runTest {
        withDatabase { database ->
            seed(database)
            conversation(database, "group", ConversationEntity.Type.GROUP, T2) { copy(wireCell = "cell") }
            val unavailable = database.sharePicker(SELF, BuildFileRestrictionState.NoRestriction)
            assertEquals(false, unavailable.conversations.single { it.value == "group" }.acceptsFiles)
            database.userConfigDAO.setWireCellsConfig(
                com.wire.kalium.persistence.config.WireCellsConfigEntity("https://cells.test", "none", null)
            )
            val picker = database.sharePicker(SELF, BuildFileRestrictionState.NoRestriction)
            assertEquals(true, picker.conversations.single { it.value == "group" }.acceptsFiles)
            val id = outbox.handOff(request(PHOTO, VIDEO), listOf(file("photo"), file("video")))
            val sending = RecordingSending(database)
            server(database, sending).importShares()
            assertEquals(listOf("Cells:photo.jpg,video.mp4:Hello as t"), sending.stored)
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), outbox.state(id))
        }
    }

    @Test
    fun appPreparesDraftWithoutSendingThenConsumesItWithoutReupload() = runTest {
        withDatabase { database ->
            seed(database)
            conversation(database, "group", ConversationEntity.Type.GROUP, T2) { copy(wireCell = "cell") }
            val store = ShareDraftStore(root.toString(), SELF)
            val path = file("photo")
            val draft = store.create(ConversationId("group", SELF.domain), ShareFile(path, PHOTO.name, PHOTO.mimeType, PHOTO.sizeInBytes, PHOTO.width, PHOTO.height))
            val preparation = request(PHOTO).copy(draftAction = "prepare", draftId = draft)
            outbox.handOff(preparation, listOf(path))
            val sending = RecordingSending(database)
            server(database, sending).importShares()
            assertEquals(1, sending.prepared)
            assertEquals(emptyList(), sending.stored)
            assertEquals("version", store.read(draft)?.asset?.version)
            outbox.handOff(request(PHOTO).copy(preparedDraftIds = listOf(draft)), listOf(path))
            server(database, sending).importShares()
            assertEquals(1, sending.prepared)
            assertEquals(listOf("Prepared:1:Hello"), sending.stored)
            assertNull(store.read(draft))
        }
    }

    @Test
    fun theAppStoresAHandedOffShareFilesFirstAndReportsItOnce() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, VIDEO), listOf(file("photo"), file("video")))
            assertEquals(HandOffState.Waiting, outbox.state(id))

            val sending = RecordingSending(database)
            server(database, sending).importShares()

            assertEquals(listOf("photo.jpg=photo as m1", "video.mp4=video as m2", "Hello as t"), sending.stored)
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), outbox.state(id))
            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(id), "A result is reported once")
        }
    }

    @Test
    fun anInterruptedImportStoresOnlyWhatItHadNotStored() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, VIDEO), listOf(file("photo"), file("video")))

            // The app goes to the background after its storage took the second file, before the message was stored.
            val interrupted = RecordingSending(database, cancelOn = "m2")
            assertFailsWith<CancellationException> { server(database, interrupted).importShares() }
            assertEquals(HandOffState.Claimed, outbox.state(id))

            val sending = RecordingSending(database)
            server(database, sending).importShares()

            assertEquals(listOf("video.mp4=video as m2", "Hello as t"), sending.stored)
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), outbox.state(id))
        }
    }

    @Test
    fun aConversationTheAppNoLongerSendsToStoresNothing() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))

            val sending = RecordingSending(database)
            server(database, sending).importShares()

            assertEquals(emptyList(), sending.stored)
            assertEquals(HandOffState.Finished(ShareBlock.UNAVAILABLE.name), outbox.state(id))
        }
    }

    @Test
    fun anImportThatFailsEndsWithWhatItStored() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(), emptyList())

            server(database, object : ShareOutboxSending by RecordingSending(database) {
                        override suspend fun text(conversationId: ConversationId, text: String, messageId: String): String? =
                    error("The app failed")
            }).importShares()

            assertEquals(HandOffState.Finished("FAILED"), outbox.state(id))
        }
    }

    @Test
    fun theAppListsConversationsOnlyWhenAsked() = runTest {
        withDatabase { database ->
            seed(database)
            server(database, RecordingSending(database)).answerConversations()
            assertNull(outbox.takeConversations())

            outbox.askForConversations()
            server(database, RecordingSending(database)).answerConversations()

            assertEquals(database.sharePicker(SELF, BuildFileRestrictionState.NoRestriction), outbox.takeConversations())
            assertNull(outbox.takeConversations(), "An answer is read once")
            assertEquals(listOf("group"), database.sharePicker(SELF, BuildFileRestrictionState.NoRestriction).conversations.map { it.value })
        }
    }

    @Test
    fun aShareWaitsForTheAccountToCatchUpThenFollowsItsCurrentRules() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO), listOf(file("photo")))
            val synced = CompletableDeferred<Unit>()
            val sending = RecordingSending(database, synced = synced)

            val importing = launch { server(database, sending).importShares() }
            withContext(Dispatchers.Default) { withTimeout(10.seconds) { sending.waitingForSync.await() } }
            assertEquals(HandOffState.Claimed, outbox.state(id))
            // Catching up turns file sharing off.
            UserConfigDataSource(database.userPrefsDAO, database.userConfigDAO, lazy { BuildFileRestrictionState.NoRestriction })
                .setFileSharingStatus(status = false, isStatusChanged = false)
            synced.complete(Unit)
            importing.join()

            assertEquals(emptyList(), sending.stored)
            assertEquals(HandOffState.Finished(FileRefusal.FILE_SHARING_DISABLED.name), outbox.state(id))
        }
    }

    @Test
    fun aSheetGetsItsListWhileAShareIsBeingImported() = runTest {
        withDatabase { database ->
            seed(database)
            val doorbell = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            val importing = CompletableDeferred<Unit>()
            val stuck = object : ShareOutboxSending by RecordingSending(database) {
                override suspend fun file(conversationId: ConversationId, file: ShareOutboxFile, messageId: String): String? {
                    importing.await()
                    return null
                }
            }
            val serving = launch { server(database, stuck).serve(doorbell) }
            outbox.handOff(request(PHOTO), listOf(file("photo")))
            doorbell.emit(Unit)
            runCurrent()

            outbox.askForConversations()
            doorbell.emit(Unit)

            assertEquals(listOf("group"), awaitConversations().conversations.map { it.value })
            serving.cancelAndJoin()
        }
    }

    @Test
    fun loggingOutDropsWhatTheOutboxHolds() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO), listOf(file("photo")))
            outbox.askForConversations()
            server(database, RecordingSending(database)).answerConversations()
            val queued = outbox.handOff(request(), emptyList())

            deleteShareOutbox(root.toString(), SELF)

            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(id))
            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(queued))
            assertNull(outbox.takeConversations())
            assertEquals(listOf("share-outbox"), FileSystem.SYSTEM.list(root).map { it.name } - "prepared")
            assertEquals(emptyList(), FileSystem.SYSTEM.list(root / "share-outbox"))
        }
    }

    /** The database answers on its own thread, so this waits in real time for the app's answer. */
    private suspend fun awaitConversations(): SharePicker = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) {
            var answer = outbox.takeConversations()
            while (answer == null) {
                delay(10.milliseconds)
                answer = outbox.takeConversations()
            }
            answer
        }
    }

    private fun server(database: UserDatabaseBuilder, sending: ShareOutboxSending) =
        ShareOutboxServer(root.toString(), SELF, database, BuildFileRestrictionState.NoRestriction, sending)

    /** Stores each item as a message, as the app's use cases would, and records it. */
    private class RecordingSending(
        private val database: UserDatabaseBuilder,
        private val cancelOn: String? = null,
        private val synced: CompletableDeferred<Unit>? = null,
    ) : ShareOutboxSending {
        val stored = mutableListOf<String>()
        val waitingForSync = CompletableDeferred<Unit>()

        override suspend fun awaitSynced() {
            waitingForSync.complete(Unit)
            synced?.await()
        }

        override suspend fun file(conversationId: ConversationId, file: ShareOutboxFile, messageId: String): String? {
            if (messageId == cancelOn) {
                FileSystem.SYSTEM.delete(file.path.toPath())
                throw CancellationException("Backgrounded")
            }
            val content = FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() }
            store(conversationId, messageId, "${file.name}=$content as $messageId")
            return null
        }

        override suspend fun cells(
            conversationId: ConversationId, files: List<ShareOutboxFile>, text: String, messageId: String,
        ): String? {
            files.forEach { assertEquals(true, FileSystem.SYSTEM.exists(it.path.toPath())) }
            store(conversationId, messageId, "Cells:${files.joinToString(",") { it.name }}:$text as $messageId")
            if (messageId == cancelOn) throw CancellationException("Backgrounded after storage")
            return null
        }

        var prepared = 0
        override suspend fun prepareCells(
            conversationId: ConversationId, files: List<ShareOutboxFile>, progress: (Long, Long) -> Unit,
        ): Either<String, List<CellAssetContent>> {
            prepared++
            progress(1, 2)
            return Either.Right(files.map {
                CellAssetContent("node", "version", it.mimeType, "cell/${it.name}", it.sizeInBytes,
                    metadata = it.metadata(), transferStatus = AssetTransferStatus.NOT_DOWNLOADED)
            })
        }
        override suspend fun discardCells(attachments: List<CellAssetContent>) = Unit
        override suspend fun storeCells(
            conversationId: ConversationId, attachments: List<CellAssetContent>, text: String, messageId: String,
        ): String? {
            store(conversationId, messageId, "Prepared:${attachments.size}:$text")
            return null
        }

        override suspend fun text(conversationId: ConversationId, text: String, messageId: String): String? {
            store(conversationId, messageId, "$text as $messageId")
            return null
        }

        private suspend fun store(conversationId: ConversationId, messageId: String, record: String) {
            val message = sharedMessage(
                messageId, MessageContent.Text(record), conversationId, SELF, ClientId("client"), T1, SelfDeletionTimer.Disabled, false
            )
            database.messageDAO.insertOrIgnoreMessage(RegularMessageMapper(SELF).toEntity(message))
            stored += record
        }
    }

    private suspend fun seed(database: UserDatabaseBuilder) {
        database.userDAO.upsertUsers(listOf(user(SELF, ConnectionEntity.State.ACCEPTED), user(OTHER, ConnectionEntity.State.ACCEPTED)))
        conversation(database, "group", ConversationEntity.Type.GROUP, T2)
        conversation(database, "left", ConversationEntity.Type.GROUP, T1, selfIsMember = false)
        UserConfigDataSource(database.userPrefsDAO, database.userConfigDAO, lazy { BuildFileRestrictionState.NoRestriction })
            .setFileSharingStatus(status = true, isStatusChanged = false)
    }

    /** A file the extension prepared, holding [content]. */
    private fun file(content: String): String {
        val path: Path = root / "prepared" / content
        FileSystem.SYSTEM.createDirectories(path.parent!!)
        FileSystem.SYSTEM.write(path) { writeUtf8(content) }
        return path.toString()
    }

    private companion object {
        val PHOTO = OutboxFile("m1", "photo.jpg", "image/jpeg", sizeInBytes = 5, width = 4, height = 3)
        val VIDEO = OutboxFile("m2", "video.mp4", "video/mp4", sizeInBytes = 5, durationMs = 1_000)

        fun request(vararg files: OutboxFile, conversation: String = "group") =
            OutboxRequest(conversation, DOMAIN, files.toList(), text = "Hello", textMessageId = "t")
    }
}
