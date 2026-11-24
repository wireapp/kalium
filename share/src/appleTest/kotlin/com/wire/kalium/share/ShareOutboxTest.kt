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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.appleAccountFileKey
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ShareOutboxTest {

    @OptIn(ExperimentalUuidApi::class)
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-outbox-test-${Uuid.random()}"
    private val outbox = ShareOutbox(root.toString(), SELF)
    private val refusals = mutableListOf<String>()

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
            assertEquals(FINISHED, state(id))
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
            assertEquals(FINISHED, state(id))
        }
    }

    @Test
    fun aCellsShareWhoseUploadFailedIsUploadedAgainWithoutARing() = runTest {
        withDatabase { database ->
            seed(database)
            conversation(database, "group", ConversationEntity.Type.GROUP, T2) { copy(wireCell = "cell") }
            val id = outbox.handOff(request(PHOTO), listOf(file("photo")))
            val sending = RecordingSending(database, failedCellsUploads = 1)
            // The doorbell never rings: serving imports at its start, then again on its own after the failed upload.
            val serving = launch { server(database, sending).serve(MutableSharedFlow()) }
            awaitFinished(id)
            assertEquals(1, sending.stored.size)
            serving.cancelAndJoin()
        }
    }

    @Test
    fun theAppStoresAHandedOffShareFilesFirst() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, VIDEO), listOf(file("photo"), file("video")))
            assertEquals(WAITING, state(id))

            val sending = RecordingSending(database)
            server(database, sending).importShares()

            assertEquals(listOf("photo.jpg=photo as m1", "video.mp4=video as m2", "Hello as t"), sending.stored)
            assertEquals(FINISHED, state(id))
            assertEquals(emptyList(), refusals)
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
            assertEquals(CLAIMED, state(id))

            val sending = RecordingSending(database)
            server(database, sending).importShares()

            assertEquals(listOf("video.mp4=video as m2", "Hello as t"), sending.stored)
            assertEquals(FINISHED, state(id))
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
            assertEquals(FINISHED, state(id))
            assertEquals(listOf(ShareBlock.UNAVAILABLE.name), refusals)
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

            assertEquals(FINISHED, state(id))
            assertEquals(listOf("FAILED"), refusals)
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
            assertEquals(CLAIMED, state(id))
            // Catching up turns file sharing off.
            UserConfigDataSource(database.userPrefsDAO, database.userConfigDAO, lazy { BuildFileRestrictionState.NoRestriction })
                .setFileSharingStatus(status = false, isStatusChanged = false)
            synced.complete(Unit)
            importing.join()

            assertEquals(emptyList(), sending.stored)
            assertEquals(FINISHED, state(id))
            assertEquals(listOf(FileRefusal.FILE_SHARING_DISABLED.name), refusals)
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
    fun aShareHandedOffWhileAnImportWaitsForTheAccountIsClaimedAtOnce() = runTest {
        withDatabase { database ->
            seed(database)
            val doorbell = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            val synced = CompletableDeferred<Unit>()
            val sending = RecordingSending(database, synced = synced)
            val first = outbox.handOff(request(PHOTO), listOf(file("photo")))
            val serving = launch { server(database, sending).serve(doorbell) }
            runCurrent()
            assertTrue(sending.waitingForSync.isCompleted)

            val second = outbox.handOff(request(textMessageId = "t2"), emptyList())
            doorbell.emit(Unit)
            runCurrent()
            assertEquals(CLAIMED, state(second))

            // The account catches up, and the app stores both, in order.
            synced.complete(Unit)
            awaitFinished(first)
            awaitFinished(second)
            assertEquals(listOf("photo.jpg=photo as m1", "Hello as t", "Hello as t2"), sending.stored)
            serving.cancelAndJoin()
        }
    }

    @Test
    fun theAppTellsTheUserOfEveryShareItDidNotStore() = runTest {
        withDatabase { database ->
            seed(database)
            outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))
            outbox.handOff(request(textMessageId = "t2"), emptyList())
            val reports = mutableListOf<Triple<ConversationId, String?, String>>()

            server(database, RecordingSending(database)) { conversation, name, reason -> reports += Triple(conversation, name, reason) }
                .importShares()

            val expected = Triple<ConversationId, String?, String>(ConversationId("left", DOMAIN), "left", ShareBlock.UNAVAILABLE.name)
            assertEquals(listOf(expected), reports)
        }
    }

    @Test
    fun draftWorkAnEarlierBuildQueuedIsDroppedWithoutSendingOrReporting() = runTest {
        withDatabase { database ->
            seed(database)
            conversation(database, "group", ConversationEntity.Type.GROUP, T2) { copy(wireCell = "cell") }
            val prepare = outbox.handOff(request(PHOTO).copy(draftAction = "prepare"), listOf(file("photo")))
            val discard = outbox.handOff(request(conversation = "left").copy(draftAction = "discard"), emptyList())
            val sending = RecordingSending(database)
            val reports = mutableListOf<String>()

            server(database, sending) { _, _, reason -> reports += reason }.importShares()

            assertEquals(emptyList(), sending.stored)
            assertTrue(reports.isEmpty())
            assertEquals(listOf(FINISHED, FINISHED), listOf(state(prepare), state(discard)))
        }
    }

    @Test
    fun aRefusalIsReportedEvenWhenTheAppStopsServingMeanwhile() = runTest {
        withDatabase { database ->
            seed(database)
            outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))
            val reporting = CompletableDeferred<Unit>()
            val goOn = CompletableDeferred<Unit>()
            val reported = CompletableDeferred<Unit>()

            val importing = launch {
                server(database, RecordingSending(database)) { _, _, _ ->
                    reporting.complete(Unit)
                    goOn.await()
                    reported.complete(Unit)
                }.importShares()
            }
            withContext(Dispatchers.Default) { withTimeout(10.seconds) { reporting.await() } }
            // The app goes to the background while it reports.
            importing.cancel()
            goOn.complete(Unit)
            importing.join()

            assertTrue(reported.isCompleted)
        }
    }

    @Test
    fun aListOfConversationsNoSheetWaitsForIsSwept() = runTest {
        withDatabase { database ->
            seed(database)
            val later = Clock.System.now() + 2.minutes
            // Wire was not running when the sheet asked, and the sheet is long gone when it runs.
            outbox.askForConversations()
            outbox.sweep(later)
            server(database, RecordingSending(database)).answerConversations()
            assertNull(outbox.takeConversations())

            // The sheet closed before it read the answer.
            outbox.askForConversations()
            outbox.sweep()
            server(database, RecordingSending(database)).answerConversations()
            outbox.sweep(later)
            assertNull(outbox.takeConversations())
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
            // Draft uploads an earlier build left behind.
            FileSystem.SYSTEM.createDirectories(root / "share-drafts" / "account")

            deleteShareOutbox(root.toString(), SELF)

            assertEquals(listOf(FINISHED, FINISHED), listOf(state(id), state(queued)))
            assertNull(outbox.takeConversations())
            assertEquals(listOf("share-outbox"), FileSystem.SYSTEM.list(root).map { it.name } - "prepared")
            assertEquals(emptyList(), FileSystem.SYSTEM.list(root / "share-outbox"))
        }
    }

    /** Where an entry is: published, with the app, or gone once the app finished it. */
    private fun state(id: String) = when {
        outbox.isWaiting(id) -> WAITING
        FileSystem.SYSTEM.exists(root / "share-outbox" / appleAccountFileKey(SELF) / "claimed" / id) -> CLAIMED
        else -> FINISHED
    }

    /** The database stores on its own thread, so this waits in real time for the app to finish the entry. */
    private suspend fun awaitFinished(id: String) = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) { while (state(id) != FINISHED) delay(10.milliseconds) }
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

    private fun server(
        database: UserDatabaseBuilder,
        sending: ShareOutboxSending,
        refused: suspend (ConversationId, String?, String) -> Unit = { _, _, reason -> refusals += reason },
    ) = ShareOutboxServer(root.toString(), SELF, database, BuildFileRestrictionState.NoRestriction, sending, refused)

    /** Stores each item as a message, as the app's use cases would, and records it. */
    private class RecordingSending(
        private val database: UserDatabaseBuilder,
        private val cancelOn: String? = null,
        private val synced: CompletableDeferred<Unit>? = null,
        private var failedCellsUploads: Int = 0,
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
            if (failedCellsUploads-- > 0) return ShareOutboxSending.UPLOAD_FAILED
            files.forEach { assertEquals(true, FileSystem.SYSTEM.exists(it.path.toPath())) }
            store(conversationId, messageId, "Cells:${files.joinToString(",") { it.name }}:$text as $messageId")
            if (messageId == cancelOn) throw CancellationException("Backgrounded after storage")
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
        const val WAITING = "waiting"
        const val CLAIMED = "claimed"
        const val FINISHED = "finished"
        val PHOTO = OutboxFile("m1", "photo.jpg", "image/jpeg", sizeInBytes = 5, width = 4, height = 3)
        val VIDEO = OutboxFile("m2", "video.mp4", "video/mp4", sizeInBytes = 5, durationMs = 1_000)

        fun request(vararg files: OutboxFile, conversation: String = "group", textMessageId: String = "t") =
            OutboxRequest(conversation, DOMAIN, files.toList(), text = "Hello", textMessageId = textMessageId)
    }
}
