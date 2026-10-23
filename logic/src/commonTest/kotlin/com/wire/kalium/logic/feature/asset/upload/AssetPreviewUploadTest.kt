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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.util.getExtensionFromMimeType
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssetPreviewUploadTest {
    private val preview = AssetContent.Preview(
        "image/jpeg", 1024, 512, 288,
        AssetContent.RemoteData(ByteArray(32) { 1 }, byteArrayOf(), "temp-preview", null, "", MessageEncryptionAlgorithm.AES_CBC),
    )

    @Test
    fun successfulUploadUsesPreviewKeyAndReplacesAllRemoteFields() = runTest {
        val assets = mock<AssetRepository>()
        val path = "local-preview.jpg".toPath()
        val extension = getExtensionFromMimeType(preview.mimeType)
        val uploaded = UploadedAssetId("uploaded-preview", "preview.domain", "preview-token")
        val hash = SHA256Key(ByteArray(32) { 4 })
        everySuspend { assets.fetchPreviewAsset(any()) } returns
            Either.Right(FetchedAssetData(path, false))
        everySuspend {
            assets.uploadAndPersistPrivateAsset(
                "image/jpeg", path, AES256Key(preview.remoteData.otrKey), extension,
                TestConversation.ID.toApi(), "preview.$extension", "image/jpeg",
            )
        } returns Either.Right(uploaded to hash)

        val result = assets.uploadPreviewIfNeeded(preview, TestConversation.ID)

        assertEquals(preview.copy(remoteData = preview.remoteData.copy(
            assetId = uploaded.key, assetDomain = uploaded.domain, assetToken = uploaded.assetToken, sha256 = hash.data,
        )), result)
        verifySuspend(VerifyMode.exactly(1)) {
            assets.uploadAndPersistPrivateAsset(
                "image/jpeg", path, AES256Key(preview.remoteData.otrKey), extension,
                TestConversation.ID.toApi(), "preview.$extension", "image/jpeg",
            )
        }
    }

    @Test
    fun optionalUploadFailureDropsPreviewWithoutFailingOriginalFlow() = runTest {
        val assets = mock<AssetRepository>()
        everySuspend { assets.fetchPreviewAsset(any()) } returns
            Either.Right(FetchedAssetData("preview.jpg".toPath(), false))
        everySuspend { assets.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any()) } returns
            Either.Left(NetworkFailure.NoNetworkConnection(null))
        assertNull(assets.uploadPreviewIfNeeded(preview, TestConversation.ID))
    }

    @Test
    fun uploadedPreviewIsReusedWhenRetryingMessageSend() = runTest {
        val assets = mock<AssetRepository>()
        val uploaded = preview.copy(remoteData = preview.remoteData.copy(sha256 = ByteArray(32) { 2 }))
        assertEquals(uploaded, assets.uploadPreviewIfNeeded(uploaded, TestConversation.ID))
        verifySuspend(VerifyMode.exactly(0)) {
            assets.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun oversizedPreviewIsDroppedBeforeCacheLookupOrUpload() = runTest {
        val assets = mock<AssetRepository>()
        val oversized = preview.copy(sizeInBytes = AssetContent.Preview.MAX_SIZE_IN_BYTES + 1)

        assertNull(assets.uploadPreviewIfNeeded(oversized, TestConversation.ID))
        assertNull(assets.uploadPreviewIfNeeded(
            oversized.copy(remoteData = oversized.remoteData.copy(sha256 = ByteArray(32) { 2 })), TestConversation.ID
        ))

        verifySuspend(VerifyMode.exactly(0)) {
            assets.fetchPreviewAsset(any())
            assets.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun malformedRemoteAndPendingPreviewsAreDroppedBeforeCacheLookupOrUpload() = runTest {
        val assets = mock<AssetRepository>()
        val invalid = listOf(
            preview.copy(mimeType = "video/mp4"),
            preview.copy(width = 0),
            preview.copy(remoteData = preview.remoteData.copy(assetId = "")),
            preview.copy(remoteData = preview.remoteData.copy(otrKey = ByteArray(31))),
            preview.copy(remoteData = preview.remoteData.copy(sha256 = ByteArray(1))),
            preview.copy(remoteData = preview.remoteData.copy(otrKey = ByteArray(33), sha256 = ByteArray(32))),
        )
        invalid.forEach { assertNull(assets.uploadPreviewIfNeeded(it, TestConversation.ID)) }
        verifySuspend(VerifyMode.exactly(0)) {
            assets.fetchPreviewAsset(any())
            assets.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun optionalUploadPreservesCancellation() = runTest {
        val assets = mock<AssetRepository>()
        everySuspend {
            assets.fetchPreviewAsset(any())
        } throws CancellationException()
        assertFailsWith<CancellationException> { assets.uploadPreviewIfNeeded(preview, TestConversation.ID) }
    }
}
