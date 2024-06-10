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

package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.message.composite.SendButtonActionConfirmationMessageUseCase.Result
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import okio.Path
import kotlin.time.Duration

@Suppress("LongParameterList")
/**
 * @sample samples.logic.MessageUseCases.sendingBasicTextMessage
 * @sample samples.logic.MessageUseCases.sendingTextMessageWithMentions
 */
class SendTextMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val assetDataSource: AssetRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val userPropertyRepository: UserPropertyRepository,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        text: String,
        linkPreviews: List<MessageLinkPreview> = emptyList(),
        mentions: List<MessageMention> = emptyList(),
        quotedMessageId: String? = null
    ): Either<CoreFailure, Unit> = scope.async(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()
        val messageTimer: Duration? = selfDeleteTimer(conversationId, true)
            .first()
            .duration

        val previews = linkPreviews.map { linkPreview ->
            val imageCopy = linkPreview.image?.let { image ->
                // Create a temporary asset key and domain
                val (generatedAssetUuid, tempAssetDomain) = uuid4().toString() to ""
                // Generate the otr asymmetric key that will be used to encrypt the data
                val otrKey = generateRandomAES256Key()
                val currentAssetMessageContent = LinkPreviewAssetMessageMetadata(
                    mimeType = image.mimeType,
                    assetDataPath = image.assetDataPath,
                    assetDataSize = image.assetDataSize,
                    assetName = image.name,
                    assetWidth = when (image.metadata) {
                        is AssetMetadata.Image -> image.metadata.width
                        else -> null
                    },
                    assetHeight = when (image.metadata) {
                        is AssetMetadata.Image -> image.metadata.height
                        else -> null
                    },
                    otrKey = otrKey,
                    // Sha256 will be replaced with right values after asset upload
                    sha256Key = SHA256Key(byteArrayOf()),
                    // Asset ID will be replaced with right value after asset upload
                    assetId = UploadedAssetId(generatedAssetUuid, tempAssetDomain),
                )

                // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
                assetDataSource.uploadAndPersistPrivateAsset(
                    currentAssetMessageContent.mimeType,
                    currentAssetMessageContent.assetDataPath,
                    currentAssetMessageContent.otrKey,
                    null
                ).fold(
                    { failure -> kaliumLogger.e("Upload asset failed: $failure") }, { (assetId, sha256) ->
                        image.copy(assetId = assetId, sha256Key = sha256, otrKey = currentAssetMessageContent.otrKey)
                    }
                )
            }
            linkPreview.copy(image = imageCopy as LinkPreviewAsset?)
        }

        previews.forEach {
            kaliumLogger.i("sha256Key: " + it?.image?.sha256Key?.data?.size)
        }

        provideClientId().flatMap { clientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Text(
                    value = text,
                    linkPreviews = previews,
                    mentions = mentions,
                    quotedMessageReference = quotedMessageId?.let { quotedMessageId ->
                        MessageContent.QuoteReference(
                            quotedMessageId = quotedMessageId,
                            quotedMessageSha256 = null,
                            isVerified = true
                        )
                    }
                ),
                expectsReadConfirmation = expectsReadConfirmation,
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.Pending,
                editStatus = Message.EditStatus.NotEdited,
                expirationData = messageTimer?.let { Message.ExpirationData(it) },
                isSelfMessage = true
            )
            persistMessage(message).flatMap {
                messageSender.sendMessage(message)
            }
        }.onFailure {
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = it,
                conversationId = conversationId,
                messageId = generatedMessageUuid,
                messageType = TYPE
            )
        }
    }.await()

    companion object {
        const val TYPE = "Text"
    }
}

private data class LinkPreviewAssetMessageMetadata(
    val mimeType: String,
    val assetId: UploadedAssetId,
    val assetDataPath: Path,
    val assetDataSize: Long,
    val assetName: String?,
    val assetWidth: Int?,
    val assetHeight: Int?,
    val otrKey: AES256Key,
    val sha256Key: SHA256Key,
)
