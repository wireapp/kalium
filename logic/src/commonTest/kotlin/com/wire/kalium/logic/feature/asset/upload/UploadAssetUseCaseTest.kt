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
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.messaging.sending.MessageSender
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test

class UploadAssetUseCaseTest {

    @Test
    fun givenAValidMessage_whenUploadStarts_thenTransferStatusUpdatedCorrectly() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadSuccess()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = eq(AssetTransferStatus.UPLOAD_IN_PROGRESS),
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenTransferStatusUpdatedCorrectly() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadSuccess()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = eq(AssetTransferStatus.UPLOADED),
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenLocalFileIsRemoved() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadSuccess()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.assetDataSource.deleteAssetLocally(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenAValidMessage_whenUploadFails_thenTransferStatusUpdatedCorrectly() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadFailure()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = eq(AssetTransferStatus.FAILED_UPLOAD),
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenAssetMessageIsPersisted() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadSuccess()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.persistMessage(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenAssetMessageIsSent() = runTest {
        val (arrangement, uploadAsset) = Arrangement()
            .withUploadSuccess()
            .arrange()

        uploadAsset(assetMessage(), uploadMetadata)

        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
    }

    private class Arrangement {

        suspend fun withUploadSuccess() = apply {
            val assetId = UploadedAssetId("remote-asset-id", "some-domain")
            val sha256Key = SHA256Key(byteArrayOf())
            coEvery {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
            }.returns(Pair(assetId, sha256Key).right())
        }

        suspend fun withUploadFailure() = apply {
            coEvery {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
            }.returns(CoreFailure.Unknown(null).left())
        }

        val assetDataSource: AssetRepository = mock(AssetRepository::class)
        val messageSender: MessageSender = mock(MessageSender::class)
        val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)
        val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase = mock(UpdateAssetMessageTransferStatusUseCase::class)
        val persistMessage: PersistMessageUseCase = mock(PersistMessageUseCase::class)

        suspend fun arrange(): Pair<Arrangement, UploadAssetUseCaseImpl> {

            coEvery {
                updateAssetMessageTransferStatus(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Success)

            coEvery {
                assetDataSource.deleteAssetLocally(any())
            } returns(Unit.right())

            coEvery {
                persistMessage(any())
            }.returns(Unit.right())

            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Unit.right())

            return this to UploadAssetUseCaseImpl(
                assetDataSource,
                messageSender,
                messageSendFailureHandler,
                updateAssetMessageTransferStatus,
                persistMessage
            )

        }
    }

    private companion object {
        private val uploadMetadata = UploadAssetMessageMetadata(
            conversationId = QualifiedID("some-id", "some-domain"),
            mimeType = "",
            assetId = UploadedAssetId("some-asset-id", "some-domain"),
            assetDataPath = "/some/path/to/asset".toPath(),
            assetDataSize = 1L,
            assetName = "",
            assetWidth = null,
            assetHeight = null,
            otrKey = AES256Key(byteArrayOf()),
            sha256Key = SHA256Key(byteArrayOf()),
            audioLengthInMs = 0L,
        )
    }
}
