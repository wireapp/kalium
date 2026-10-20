/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.di.GlobalBinding
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.MessageAssetResult
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCaseImpl
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.sync.Result
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Uses the generated assisted factory; repository lookups and writes use the real use cases. */
class AudioNormalizedLoudnessWorkerBindingTest {
    @Test
    fun creatingTheFactoryAndWorkerDoesNotResolveAnyUseCase() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val factory = fixture.graph().workerFactory
        fixture.assertCounts(0, 0, 0)
        assertEquals(0, fixture.builderCaptures)

        factory.create(CONVERSATION_ID, MESSAGE_ID)

        fixture.assertCounts(0, 0, 0)
        assertEquals(1, fixture.builderCaptures)
        assertTrue(fixture.builderPaths.isEmpty())
        assertEquals(listOf("builder.capture"), fixture.events)
    }

    @Test
    fun repeatedMissingMessageWorkResolvesAndInvokesAFreshLookupEachTime() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
        val worker = fixture.worker()

        repeat(2) { assertSame(Result.Failure, worker.doWork()) }

        fixture.assertCounts(2, 0, 0)
        assertNotSame(fixture.lookups[0], fixture.lookups[1])
        assertEquals(2, fixture.lookupCalls)
        assertEquals(1, fixture.builderCaptures)
        assertTrue(fixture.builderPaths.isEmpty())
    }

    @Test
    fun nonAssetNonAudioAndAlreadyNormalizedMetadataNeverResolveDownloadOrUpdate() = runTest {
        val dispatchers = StandardTestDispatcher(testScheduler).testKaliumDispatcher()
        val skippedContent = listOf(
            TestMessage.TEXT_CONTENT,
            asset(null),
            asset(AssetMetadata.Image(width = 32, height = 16)),
            asset(AssetMetadata.Video(width = null, height = null, durationMs = null)),
            asset(AssetMetadata.Audio(durationMs = null, normalizedLoudness = byteArrayOf())),
            asset(AssetMetadata.Audio(durationMs = 100, normalizedLoudness = byteArrayOf(1))),
        )
        for (content in skippedContent) {
            val fixture = Fixture(dispatchers).apply { show(content) }

            assertSame(Result.Failure, fixture.worker().doWork(), "Content: $content")

            fixture.assertCounts(1, 0, 0)
            assertEquals(1, fixture.lookupCalls)
            assertTrue(fixture.builderPaths.isEmpty())
            assertEquals(0, fixture.updateCalls)
        }
    }

    @Test
    fun successAwaitsTheOriginalDeferredThenPassesItsPathAndExactBuilderBytesToTheUpdate() = runTest {
        val bytes = byteArrayOf(0, 127, -1)
        val assetResult = CompletableDeferred<MessageAssetResult>()
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            show(asset(AssetMetadata.Audio(durationMs = null, normalizedLoudness = null), mimeType = "application/octet-stream"))
            download(assetResult)
            selectedBuilder = builderReturning(bytes)
            expectUpdates(bytes)
        }
        val worker = fixture.worker()
        val result = async { worker.doWork() }
        fixture.assetRequested.await()

        assertFalse(result.isCompleted)
        fixture.assertCounts(1, 1, 0)
        assertTrue(fixture.builderPaths.isEmpty())
        assertEquals(0, fixture.updateCalls)

        assetResult.complete(downloadedAsset())

        assertSame(Result.Success, result.await())
        fixture.assertCounts(1, 1, 1)
        assertEquals(listOf(DECODED_PATH), fixture.builderPaths)
        assertEquals(1, fixture.updateCalls)
        assertEquals(
            listOf(
                "builder.capture", "lookup.create", "lookup.invoke", "download.create",
                "download.invoke", "builder.invoke", "update.create", "update.invoke",
            ),
            fixture.events,
        )
    }

    @Test
    fun repeatedSuccessfulWorkGetsThreeFreshUseCasesWithoutRecapturingTheBuilder() = runTest {
        val bytes = byteArrayOf(17, 34)
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            selectedBuilder = builderReturning(bytes)
            expectUpdates(bytes, bytes)
        }
        val worker = fixture.worker()

        repeat(2) { assertSame(Result.Success, worker.doWork()) }

        fixture.assertCounts(2, 2, 2)
        assertNotSame(fixture.lookups[0], fixture.lookups[1])
        assertNotSame(fixture.downloads[0], fixture.downloads[1])
        assertNotSame(fixture.updates[0], fixture.updates[1])
        assertEquals(2, fixture.lookupCalls)
        assertEquals(2, fixture.updateCalls)
        assertEquals(listOf(DECODED_PATH, DECODED_PATH), fixture.builderPaths)
        assertEquals(1, fixture.builderCaptures)
    }

    @Test
    fun eachWorkerCapturesTheBuilderAtConstructionEvenWhenWorkStartsLater() = runTest {
        val firstBytes = byteArrayOf(1)
        val secondBytes = byteArrayOf(2)
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            expectUpdates(firstBytes, secondBytes)
            selectedBuilder = builderReturning(firstBytes)
        }
        val factory = fixture.graph().workerFactory
        val first = factory.create(CONVERSATION_ID, MESSAGE_ID)
        fixture.selectedBuilder = fixture.builderReturning(secondBytes)
        val second = factory.create(CONVERSATION_ID, MESSAGE_ID)
        fixture.selectedBuilder = AudioNormalizedLoudnessBuilder { error("Builder must already be captured") }
        fixture.assertCounts(0, 0, 0)
        assertEquals(2, fixture.builderCaptures)

        assertSame(Result.Success, first.doWork())
        assertSame(Result.Success, second.doWork())

        assertEquals(2, fixture.builderCaptures)
        assertEquals(2, fixture.updateCalls)
    }

    @Test
    fun aFreshLookupSeesNewNormalizationAndSkipsDownloadOnTheNextWorkExecution() = runTest {
        val bytes = byteArrayOf(9)
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            selectedBuilder = builderReturning(bytes)
            expectUpdates(bytes)
        }
        val worker = fixture.worker()
        assertSame(Result.Success, worker.doWork())
        fixture.show(asset(AssetMetadata.Audio(durationMs = null, normalizedLoudness = bytes)))

        assertSame(Result.Failure, worker.doWork())

        fixture.assertCounts(2, 1, 1)
        assertNotSame(fixture.lookups[0], fixture.lookups[1])
        assertEquals(2, fixture.lookupCalls)
        assertEquals(1, fixture.updateCalls)
        assertEquals(listOf(DECODED_PATH), fixture.builderPaths)
    }

    @Test
    fun failedAssetFetchDoesNotInvokeTheBuilderOrResolveAnUpdate() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(MessageAssetResult.Failure(StorageFailure.DataNotFound, isRetryNeeded = true)))
        }

        assertSame(Result.Failure, fixture.worker().doWork())

        fixture.assertCounts(1, 1, 0)
        assertTrue(fixture.builderPaths.isEmpty())
        assertEquals(0, fixture.updateCalls)
    }

    @Test
    fun missingBuilderBytesDoesNotResolveAnUpdate() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            selectedBuilder = builderReturning(null)
        }

        assertSame(Result.Failure, fixture.worker().doWork())

        fixture.assertCounts(1, 1, 0)
        assertEquals(listOf(DECODED_PATH), fixture.builderPaths)
        assertEquals(0, fixture.updateCalls)
    }

    @Test
    fun aReturnedUpdateFailurePreservesTheExistingWorkerSuccessResult() = runTest {
        val bytes = byteArrayOf() // Non-null empty output must still reach the update.
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            selectedBuilder = builderReturning(bytes)
            expectUpdates(bytes)
            updateResponse = Either.Left(StorageFailure.DataNotFound)
        }

        assertSame(Result.Success, fixture.worker().doWork())

        fixture.assertCounts(1, 1, 1)
        assertEquals(1, fixture.updateCalls)
    }

    @Test
    fun cancellingTheWorkerWhileAwaitingDoesNotCancelTheExternallyOwnedAssetFetch() = runTest {
        val externalOwner = SupervisorJob()
        val assetResult = CompletableDeferred<MessageAssetResult>(externalOwner)
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(assetResult)
        }
        val worker = fixture.worker()
        val result = async { worker.doWork() }
        try {
            fixture.assetRequested.await()
            result.cancelAndJoin()

            assertTrue(result.isCancelled)
            assertFalse(assetResult.isCancelled)
            assertFalse(assetResult.isCompleted)
            assertTrue(externalOwner.isActive)
            fixture.assertCounts(1, 1, 0)
            assertTrue(fixture.builderPaths.isEmpty())
            assertEquals(0, fixture.updateCalls)
            assertTrue(assetResult.complete(downloadedAsset()))
        } finally {
            result.cancelAndJoin()
            externalOwner.cancelAndJoin()
        }
    }

    @Test
    fun builderExceptionsStillPropagateWithoutResolvingAnUpdate() = runTest {
        val failure = IllegalStateException("decoder failed")
        val fixture = Fixture(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).apply {
            showAudio()
            download(CompletableDeferred(downloadedAsset()))
            selectedBuilder = AudioNormalizedLoudnessBuilder { throw failure }
        }

        assertSame(failure, assertFailsWith<IllegalStateException> { fixture.worker().doWork() })

        fixture.assertCounts(1, 1, 0)
        assertEquals(0, fixture.updateCalls)
    }

    private class Fixture(private val dispatchers: KaliumDispatcher) : AudioWorkerTestFactories {
        val events = mutableListOf<String>()
        val lookups = mutableListOf<GetMessageByIdUseCase>()
        val downloads = mutableListOf<GetMessageAssetUseCase>()
        val updates = mutableListOf<UpdateAudioMessageNormalizedLoudnessUseCase>()
        val builderPaths = mutableListOf<String>()
        val assetRequested = CompletableDeferred<Unit>()
        var lookupCalls = 0
        var updateCalls = 0
        var builderCaptures = 0
        var updateResponse: Either<CoreFailure, Unit> = Either.Right(Unit)
        var selectedBuilder = AudioNormalizedLoudnessBuilder { error("Unexpected builder invocation") }
        private var lookupResponse: Either<StorageFailure, Message> = Either.Left(StorageFailure.DataNotFound)
        private var assetResponse: (() -> Deferred<MessageAssetResult>)? = null
        private var expectedUpdates = emptyList<ByteArray>()

        // Unused repository methods remain strict. Coordinates and bytes are checked at the actual repository boundary.
        private val repository = object : MessageRepository by mock<MessageRepository>(mode = MockMode.strict) {
            override suspend fun getMessageById(
                conversationId: ConversationId,
                messageUuid: String,
            ): Either<StorageFailure, Message> {
                assertEquals(CONVERSATION_ID, conversationId)
                assertEquals(MESSAGE_ID, messageUuid)
                events += "lookup.invoke"
                lookupCalls++
                return lookupResponse
            }

            override suspend fun updateAudioMessageNormalizedLoudness(
                conversationId: ConversationId,
                messageId: String,
                normalizedLoudness: ByteArray,
            ): Either<CoreFailure, Unit> {
                assertEquals(CONVERSATION_ID, conversationId)
                assertEquals(MESSAGE_ID, messageId)
                assertTrue(updateCalls < expectedUpdates.size, "Unexpected update")
                assertSame(expectedUpdates[updateCalls], normalizedLoudness)
                events += "update.invoke"
                updateCalls++
                return updateResponse
            }
        }

        override val builder: AudioNormalizedLoudnessBuilder
            get() {
                events += "builder.capture"
                builderCaptures++
                return selectedBuilder
            }

        override fun newLookup(): GetMessageByIdUseCase {
            events += "lookup.create"
            return GetMessageByIdUseCase(repository, dispatchers).also(lookups::add)
        }

        override fun newDownload(): GetMessageAssetUseCase {
            val response = checkNotNull(assetResponse) { "Download must remain unresolved on this branch" }
            events += "download.create"
            return object : GetMessageAssetUseCase {
                override suspend fun invoke(conversationId: ConversationId, messageId: String): Deferred<MessageAssetResult> {
                    assertEquals(CONVERSATION_ID, conversationId)
                    assertEquals(MESSAGE_ID, messageId)
                    events += "download.invoke"
                    assetRequested.complete(Unit)
                    return response()
                }
            }.also(downloads::add)
        }

        override fun newUpdate(): UpdateAudioMessageNormalizedLoudnessUseCase {
            check(expectedUpdates.isNotEmpty()) { "Update must remain unresolved on this branch" }
            events += "update.create"
            return UpdateAudioMessageNormalizedLoudnessUseCaseImpl(repository, dispatchers).also(updates::add)
        }

        fun graph(): AudioWorkerBindingTestGraph = createGraphFactory<AudioWorkerBindingTestGraph.Factory>().create(this)

        fun worker(): AudioNormalizedLoudnessWorker = graph().workerFactory.create(CONVERSATION_ID, MESSAGE_ID)

        fun show(content: MessageContent.Regular) {
            lookupResponse = Either.Right(
                TestMessage.TEXT_MESSAGE.copy(id = MESSAGE_ID, conversationId = CONVERSATION_ID, content = content)
            )
        }

        fun showAudio() = show(asset(AssetMetadata.Audio(durationMs = 200, normalizedLoudness = null)))

        fun download(result: Deferred<MessageAssetResult>) {
            assetResponse = { result }
        }

        fun expectUpdates(vararg bytes: ByteArray) {
            expectedUpdates = bytes.toList()
        }

        fun builderReturning(bytes: ByteArray?) = AudioNormalizedLoudnessBuilder { path ->
            events += "builder.invoke"
            builderPaths += path
            bytes
        }

        fun assertCounts(lookup: Int, download: Int, update: Int) {
            assertEquals(lookup, lookups.size, "Lookup constructions")
            assertEquals(download, downloads.size, "Download constructions")
            assertEquals(update, updates.size, "Update constructions")
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("audio-conversation", "example.com")
        const val MESSAGE_ID = "audio-message"
        const val DECODED_PATH = "/caller-owned/assets/decoded audio.ogg"

        fun asset(metadata: AssetMetadata?, mimeType: String = "audio/ogg") = MessageContent.Asset(
            TestMessage.ASSET_CONTENT.value.copy(name = "original-name.ogg", metadata = metadata, mimeType = mimeType)
        )

        fun downloadedAsset() = MessageAssetResult.Success(DECODED_PATH.toPath(), assetSize = 123, assetName = "downloaded-name.ogg")
    }
}

/** Only test factory counters and one builder value enter this graph; no facade or consumer is prebuilt. */
internal interface AudioWorkerTestFactories {
    fun newLookup(): GetMessageByIdUseCase
    fun newDownload(): GetMessageAssetUseCase
    fun newUpdate(): UpdateAudioMessageNormalizedLoudnessUseCase
    val builder: AudioNormalizedLoudnessBuilder
}

@DependencyGraph
internal interface AudioWorkerBindingTestGraph {
    val workerFactory: AudioNormalizedLoudnessWorkerImpl.Factory

    @Provides
    @LegacyMessageBinding("getMessageById")
    fun provideLookup(factories: AudioWorkerTestFactories): GetMessageByIdUseCase = factories.newLookup()

    @Provides
    @LegacyMessageBinding("getAssetMessage")
    fun provideDownload(factories: AudioWorkerTestFactories): GetMessageAssetUseCase = factories.newDownload()

    @Provides
    @LegacyMessageBinding("updateAudioMessageNormalizedLoudnessUseCase")
    fun provideUpdate(factories: AudioWorkerTestFactories): UpdateAudioMessageNormalizedLoudnessUseCase = factories.newUpdate()

    @Provides
    @GlobalBinding("audioNormalizedLoudnessBuilder")
    fun provideBuilder(factories: AudioWorkerTestFactories): AudioNormalizedLoudnessBuilder = factories.builder

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides factories: AudioWorkerTestFactories): AudioWorkerBindingTestGraph
    }
}
