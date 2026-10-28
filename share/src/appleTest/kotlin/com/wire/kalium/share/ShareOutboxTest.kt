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
            assertEquals(HandOffState.Claimed, outbox.state(second))

            // The account catches up, and the app stores both, in order.
            synced.complete(Unit)
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), awaitResult(first))
            assertEquals(HandOffState.Finished(ShareOutbox.STORED), awaitResult(second))
            assertEquals(listOf("photo.jpg=photo as m1", "Hello as t", "Hello as t2"), sending.stored)
            serving.cancelAndJoin()
        }
    }

    @Test
    fun theAppReportsARefusalOnlyOnceTheSheetStoppedWaiting() = runTest {
        withDatabase { database ->
            seed(database)
            val refused = outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))
            val stored = outbox.handOff(request(textMessageId = "t2"), emptyList())
            // The sheet waited only seconds for the app.
            assertNull(outbox.stopWaiting(refused))
            assertNull(outbox.stopWaiting(stored))
            val reports = mutableListOf<Triple<ConversationId, String?, String>>()

            server(database, RecordingSending(database)) { conversation, name, reason -> reports += Triple(conversation, name, reason) }
                .importShares()

            val expected = Triple<ConversationId, String?, String>(ConversationId("left", DOMAIN), "left", ShareBlock.UNAVAILABLE.name)
            assertEquals(listOf(expected), reports)
            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(refused), "The app took the result")
            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(stored), "The app took the result")
        }
    }

    @Test
    fun aRefusalIsReportedEvenWhenTheAppStopsServingMeanwhile() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))
            assertNull(outbox.stopWaiting(id))
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
    fun aResultTheAppLeftBeforeTheSheetStoppedWaitingIsTheSheets() = runTest {
        withDatabase { database ->
            seed(database)
            val id = outbox.handOff(request(PHOTO, conversation = "left"), listOf(file("photo")))
            var reports = 0

            server(database, RecordingSending(database)) { _, _, _ -> reports++ }.importShares()

            assertEquals(HandOffState.Finished(ShareBlock.UNAVAILABLE.name), outbox.stopWaiting(id))
            assertEquals(0, reports)
            assertEquals(HandOffState.Finished(ShareOutbox.MISSING), outbox.state(id), "A result is reported once")
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

    /** The database stores on its own thread, so this waits in real time for the app's result. */
    private suspend fun awaitResult(id: String): HandOffState = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) {
            var state = outbox.state(id)
            while (state !is HandOffState.Finished) {
                delay(10.milliseconds)
                state = outbox.state(id)
            }
            state
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

    private fun server(
        database: UserDatabaseBuilder,
        sending: ShareOutboxSending,
        refused: suspend (ConversationId, String?, String) -> Unit = { _, _, _ -> },
    ) = ShareOutboxServer(root.toString(), SELF, database, BuildFileRestrictionState.NoRestriction, sending, refused)

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

        fun request(vararg files: OutboxFile, conversation: String = "group", textMessageId: String = "t") =
            OutboxRequest(conversation, DOMAIN, files.toList(), text = "Hello", textMessageId = textMessageId)
    }
}
