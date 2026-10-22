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

package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetAuditFeatureHandler
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.SUPPORTED_IMAGE_ASSET_MIME_TYPES
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.util.getExtensionFromMimeType
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersistAssetPreviewTest {
    @Test
    fun acceptedImagePreviewsAreCopiedWithTheirMimeExtensionAndCallerKeepsSource() = runTest {
        for (mimeType in SUPPORTED_IMAGE_ASSET_MIME_TYPES) {
            val fixture = Fixture(StandardTestDispatcher(testScheduler))
            fixture.useCase("message", TestMessage.TEST_SENDER_USER_ID, fixture.params.copy(
                preview = fixture.params.preview!!.copy(mimeType = mimeType),
            )).shouldSucceed { (metadata, _) ->
                val preview = assertNotNull(metadata.preview)
                val stored = fixture.stored.first { it.key == preview.remoteData.assetId }
                val extension = getExtensionFromMimeType(mimeType)
                val expectedName = preview.remoteData.assetId + (extension?.let { ".$it" } ?: "")
                assertEquals(expectedName, stored.dataPath.toPath().name, "Preview filename for $mimeType")
                assertEquals("preview bytes", fixture.read(stored.dataPath.toPath()))
            }
            fixture.assertCallerSourceUnchanged()
        }
    }

    @Test
    fun rejectedPreviewsLeaveCallerSourceUntouched() = runTest {
        val invalid = listOf(
            { p: AssetPreviewUploadParams -> p.copy(mimeType = "video/mp4") },
            { p: AssetPreviewUploadParams -> p.copy(assetDataSize = 200_001) },
            { p: AssetPreviewUploadParams -> p.copy(width = 0) },
            { p: AssetPreviewUploadParams -> p.copy(height = 4097) },
        )
        for (change in invalid) {
            val fixture = Fixture(StandardTestDispatcher(testScheduler))
            fixture.useCase("message", TestMessage.TEST_SENDER_USER_ID, fixture.params.copy(
                preview = change(fixture.params.preview!!),
            )).shouldSucceed { (metadata, _) -> assertNull(metadata.preview) }
            assertEquals(1, fixture.stored.size)
            fixture.assertCallerSourceUnchanged()
        }
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        fixture.useCase("message", TestMessage.TEST_SENDER_USER_ID, fixture.params.copy(
            assetMimeType = "application/pdf", preview = fixture.params.preview,
        )).shouldSucceed { (metadata, _) -> assertNull(metadata.preview) }
        fixture.assertCallerSourceUnchanged()
    }

    @Test
    fun failedPreviewPersistencePreservesCallerSourceAndRemovesOwnedCopy() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        fixture.previewInsertFailure = IOException("cannot store preview")

        fixture.useCase("message", TestMessage.TEST_SENDER_USER_ID, fixture.params)
            .shouldSucceed { (metadata, _) -> assertNull(metadata.preview) }

        fixture.assertCallerSourceUnchanged()
        assertEquals(1, fixture.stored.size)
        assertEquals(1, fixture.files.listDirectories(fixture.files.providePersistentAssetPath("unused").parent!!).size)
    }

    @Test
    fun cancelledPreviewPersistencePreservesCallerSourceAndRemovesOwnedCopy() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        fixture.previewInsertFailure = CancellationException("cancelled preview persistence")

        assertFailsWith<CancellationException> {
            fixture.useCase("message", TestMessage.TEST_SENDER_USER_ID, fixture.params)
        }

        fixture.assertCallerSourceUnchanged()
        assertEquals(1, fixture.stored.size)
        assertEquals(1, fixture.files.listDirectories(fixture.files.providePersistentAssetPath("unused").parent!!).size)
    }

    private class Fixture(dispatcher: TestDispatcher) {
        val files = FakeKaliumFileSystem()
        private val dao = mock<AssetDAO>()
        val stored = mutableListOf<AssetEntity>()
        var previewInsertFailure: Exception? = null
        private val originalSource = files.tempFilePath("caller-video.mp4")
        private val previewSource = files.tempFilePath("caller-preview.png")
        val params = AssetUploadParams(
            TestConversation.ID, originalSource, 11, "video.mp4", "video/mp4", 1920, 1080, 0, null,
            AssetPreviewUploadParams(previewSource, 13, "image/png", 512, 288),
        )
        private val properties = mock<UserPropertyRepository>()
        private val timer = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>()
        val useCase = PersistNewAssetMessageUseCaseImpl(
            PersistMessageUseCase { Either.Right(Unit) },
            CurrentClientIdProvider { Either.Right(TestMessage.TEST_SENDER_CLIENT_ID) },
            properties,
            timer,
            AssetDataSource(
                mock<AssetApi>(), dao, TestMessage.TEST_SENDER_USER_ID, AssetMapperImpl(),
                lazy { mock<AssetAuditFeatureHandler>() }, files,
            ),
            dispatcher.testKaliumDispatcher(),
        )

        init {
            files.sink(originalSource).buffer().use { it.writeUtf8("video bytes") }
            files.sink(previewSource).buffer().use { it.writeUtf8("preview bytes") }
            everySuspend { properties.getReadReceiptsStatus() } returns false
            everySuspend { timer(any(), any()) } returns flowOf(SelfDeletionTimer.Disabled)
            everySuspend { dao.isFileReferenced(any()) } calls { invocation ->
                stored.any { it.dataPath == invocation.args[0] }
            }
            everySuspend { dao.insertAsset(any()) } calls { invocation ->
                val asset = invocation.args[0] as AssetEntity
                if (stored.isNotEmpty()) previewInsertFailure?.let { throw it }
                stored.add(asset)
                Unit
            }
        }

        fun read(path: Path): String = files.source(path).buffer().use { it.readUtf8() }
        fun assertCallerSourceUnchanged() = assertEquals("preview bytes", read(previewSource))
    }
}
