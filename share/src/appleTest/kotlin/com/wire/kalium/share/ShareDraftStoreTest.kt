/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class ShareDraftStoreTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-drafts-test-${Uuid.random()}"
    private val account = UserId("self", "test.example")
    private val conversation = ConversationId("conversation", "test.example")
    private val store = ShareDraftStore(root.toString(), account)
    private val file = ShareFile("/tmp/file", "file.txt", "text/plain", 10)
    private val asset = SharedDraftAsset("node", "version", "cell/file.txt", "cell")

    @AfterTest
    fun cleanup() { FileSystem.SYSTEM.deleteRecursively(root) }

    @Test
    fun preparingReportsProgressButStoresNoMessageAndKeepsExactVersion() = runTest {
        val id = store.create(conversation, file)
        prepareShareDraft(store, id, upload = { _, progress ->
            progress(4, 10)
            assertEquals(0.4, store.read(id)?.progress)
            assertNull(store.resolve(listOf(id), conversation))
            Either.Right(asset)
        }, discard = { error("Not cancelled") })
        assertEquals("version", store.resolve(listOf(id), conversation)?.single()?.asset?.version)
        assertEquals(1.0, store.read(id)?.progress)
    }

    @Test
    fun cannotReuseDraftForAnotherConversationAccountOrDuplicateItem() = runTest {
        val id = store.create(conversation, file)
        store.write(id, requireNotNull(store.read(id)).copy(asset = asset))
        assertNull(store.resolve(listOf(id), ConversationId("other", conversation.domain)))
        assertNull(store.resolve(listOf(id, id), conversation))
        assertNull(ShareDraftStore(root.toString(), UserId("other", account.domain)).resolve(listOf(id), conversation))
        assertNotNull(store.resolve(listOf(id), conversation))
        store.cancel(id)
        assertNull(store.resolve(listOf(id), conversation))
    }

    @Test
    fun removalBeforeUploadDoesNotUpload() = runTest {
        val id = store.create(conversation, file)
        store.cancel(id)
        prepareShareDraft(store, id, upload = { _, _ -> error("Removed") }, discard = {})
    }

    @Test
    fun lateUploadAfterRemovalIsDiscardedAndNeverReady() = runTest {
        val id = store.create(conversation, file)
        val finish = CompletableDeferred<Unit>()
        var discarded = 0
        val task = launch {
            prepareShareDraft(store, id, upload = { _, _ ->
                withContext(NonCancellable) { finish.await() }
                Either.Right(asset)
            }, discard = { discarded++ })
        }
        runCurrent()
        store.cancel(id)
        advanceTimeBy(100)
        finish.complete(Unit)
        task.join()
        assertEquals(1, discarded)
        assertNull(store.resolve(listOf(id), conversation))
    }

    @Test
    fun uploadFailureIsVisibleAndDoesNotMakeDraftSendable() = runTest {
        val id = store.create(conversation, file)
        prepareShareDraft(store, id, upload = { _, _ -> Either.Left("OFFLINE") }, discard = {})
        assertEquals("OFFLINE", store.read(id)?.failure)
        assertNull(store.resolve(listOf(id), conversation))
    }
}
