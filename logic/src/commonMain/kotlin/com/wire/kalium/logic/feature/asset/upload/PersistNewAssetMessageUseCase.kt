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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.asset.isVideoMimeType
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.hasValidPendingData
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.util.getExtensionFromMimeType
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal interface PersistNewAssetMessageUseCase {
    suspend operator fun invoke(
        messageId: String,
        userId: UserId,
        asset: AssetUploadParams
    ): Either<CoreFailure, Pair<UploadAssetMessageMetadata, Message.Regular>>
}

@Suppress("LongParameterList")
internal class PersistNewAssetMessageUseCaseImpl(
    private val persistMessage: PersistMessageUseCase,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userPropertyRepository: UserPropertyRepository,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val assetDataSource: AssetRepository,
    private val dispatcher: KaliumDispatcher,
) : PersistNewAssetMessageUseCase {

    override suspend operator fun invoke(
        messageId: String,
        userId: UserId,
        asset: AssetUploadParams
    ): Either<CoreFailure, Pair<UploadAssetMessageMetadata, Message.Regular>> = currentClientIdProvider().flatMap { currentClientId ->

        // Create  temporary asset key and domain
        val (generatedAssetUuid, tempAssetDomain) = Uuid.random().toString() to ""
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        val expireAfter = selfDeleteTimer(asset.conversationId, true)
            .first()
            .duration

        withContext(dispatcher.io) {
            assetDataSource.persistAsset(
                generatedAssetUuid,
                tempAssetDomain,
                asset.assetDataPath,
                asset.assetDataSize,
                asset.assetName.fileExtension()
            )
                .flatMap { persistedAssetDataPath ->
                    // Generate the otr asymmetric key that will be used to encrypt the data
                    val currentAssetMessageContent = asset.createTempAssetMetadata(generatedAssetUuid, persistedAssetDataPath)
                        .copy(preview = persistPreview(asset))

                    val message = Message.Regular(
                        id = messageId,
                        content = MessageContent.Asset(currentAssetMessageContent.toAssetContent()),
                        conversationId = asset.conversationId,
                        date = Clock.System.now(),
                        senderUserId = userId,
                        senderClientId = currentClientId,
                        status = Message.Status.Pending,
                        editStatus = Message.EditStatus.NotEdited,
                        expectsReadConfirmation = expectsReadConfirmation,
                        expirationData = expireAfter?.let { Message.ExpirationData(it) },
                        isSelfMessage = true
                    )
                    // We persist the asset message right away so that it can be displayed on the conversation screen loading
                    persistMessage(message).onFailure {
                        currentAssetMessageContent.preview?.let { assetDataSource.deleteAssetLocally(it.remoteData.assetId) }
                    }.map { currentAssetMessageContent to message }
                }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun persistPreview(asset: AssetUploadParams): AssetContent.Preview? {
        val source = asset.preview?.takeIf { isVideoMimeType(asset.assetMimeType) } ?: return null
        val preview = AssetContent.Preview(
            mimeType = source.mimeType,
            sizeInBytes = source.assetDataSize,
            width = source.width,
            height = source.height,
            remoteData = AssetContent.RemoteData(
                otrKey = generateRandomAES256Key().data,
                sha256 = byteArrayOf(),
                assetId = Uuid.random().toString(),
                assetToken = null,
                assetDomain = "",
                encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
            ),
        ).takeIf { it.hasValidPendingData() } ?: return null
        return assetDataSource.persistPreviewAsset(
            preview.remoteData.assetId,
            source.assetDataPath,
            source.assetDataSize,
            getExtensionFromMimeType(source.mimeType),
        ).getOrNull()?.let { preview }
    }
}
