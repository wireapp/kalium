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
package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.AttachmentType
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.durationMs
import com.wire.kalium.logic.data.message.height
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.width
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import kotlin.time.Duration

/**
 * Use case to send a multipart message.
 * Sends Multipart message with multiple attachments in conversations with Cell Feature enabled.
 * For regular conversations, each attachment is sent as a separate Asset message.
 */
@Suppress("LongParameterList")
class SendMultipartMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val assetDataSource: AssetRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val userPropertyRepository: UserPropertyRepository,
    private val conversationRepository: ConversationRepository,
    private val attachmentsRepository: MessageAttachmentDraftRepository,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val publishAttachments: PublishAttachmentsUseCase,
    private val removeAttachmentDrafts: RemoveAttachmentDraftsUseCase,
    private val sendAssetMessage: ScheduleNewAssetMessageUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope
) {

    companion object {
        private const val MSG_TYPE_TEXT = "Text"
    }

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

        val isCellEnabled = conversationRepository.isCellEnabled(conversationId).getOrFail { error ->
            return@async error.left()
        }

        val attachments: List<MessageAttachment> = attachmentsRepository.getAll(conversationId)
            .getOrElse { emptyList() }
            .map {
                CellAssetContent(
                    id = it.uuid,
                    versionId = it.versionId,
                    mimeType = it.mimeType,
                    assetPath = it.remoteFilePath,
                    assetSize = it.fileSize,
                    localPath = it.localFilePath,
                    previewUrl = null,
                    metadata = it.metadata(),
                    transferStatus = AssetTransferStatus.SAVED_INTERNALLY,
                )
            }

        if (attachments.isNotEmpty()) {
            removeAttachmentDrafts(conversationId)
        }

        provideClientId().flatMap { clientId ->
            val message = buildMessage(
                conversationId,
                clientId,
                generatedMessageUuid,
                text,
                linkPreviews,
                mentions,
                attachments,
                quotedMessageId,
                isCellEnabled,
            )

            persistMessage(message).flatMap {
                sendMessage(conversationId, message, isCellEnabled, attachments)
            }

        }.onFailure {
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = it,
                conversationId = conversationId,
                messageId = generatedMessageUuid,
                messageType = MSG_TYPE_TEXT
            )
        }
    }.await()

    private suspend fun buildMessage(
        conversationId: ConversationId,
        clientId: ClientId,
        generatedMessageUuid: String,
        text: String,
        linkPreviews: List<MessageLinkPreview>,
        mentions: List<MessageMention>,
        attachments: List<MessageAttachment>,
        quotedMessageId: String?,
        isCellEnabled: Boolean,
    ): Message.Regular {

        val previews = uploadLinkPreviewImages(linkPreviews)
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()
        val messageTimer: Duration? = selfDeleteTimer(conversationId, true).first().duration

        return Message.Regular(
            id = generatedMessageUuid,
            content = if (isCellEnabled) {
                MessageContent.Multipart(
                    value = text,
                    linkPreviews = previews,
                    mentions = mentions,
                    attachments = attachments,
                    quotedMessageReference = quotedMessageId?.let {
                        MessageContent.QuoteReference(quotedMessageId, null, true)
                    }
                )
            } else {
                MessageContent.Text(
                    value = text,
                    linkPreviews = previews,
                    mentions = mentions,
                    quotedMessageReference = quotedMessageId?.let {
                        MessageContent.QuoteReference(quotedMessageId, null, true)
                    }
                )
            },
            expectsReadConfirmation = expectsReadConfirmation,
            conversationId = conversationId,
            date = Clock.System.now(),
            senderUserId = selfUserId,
            senderClientId = clientId,
            status = Message.Status.Pending,
            editStatus = Message.EditStatus.NotEdited,
            expirationData = messageTimer?.let { Message.ExpirationData(it) },
            isSelfMessage = true
        )
    }

    private suspend fun sendMessage(
        conversationId: ConversationId,
        message: Message.Regular,
        isCellEnabled: Boolean,
        attachments: List<MessageAttachment>
    ) = if (isCellEnabled) {
        publishAttachments(attachments).onSuccess {
            messageSender.sendMessage(message)
        }
    } else {
        scope.launch {
            attachments.forEach { attachment ->
                with(attachment as CellAssetContent) {
                    sendAssetMessage(
                        conversationId = conversationId,
                        assetDataPath = localPath?.toPath() ?: error(""),
                        assetDataSize = assetSize ?: 0,
                        assetName = assetPath ?: "",
                        assetMimeType = mimeType,
                        assetWidth = metadata?.width(),
                        assetHeight = metadata?.height(),
                        audioLengthInMs = metadata?.durationMs() ?: 0,
                    )
                }
            }
        }

        messageSender.sendMessage(message)
    }

    private suspend fun uploadLinkPreviewImages(linkPreviews: List<MessageLinkPreview>): List<MessageLinkPreview> {
        return linkPreviews.map { linkPreview ->
            val imageCopy = linkPreview.image?.let {
                // Generate the otr asymmetric key that will be used to encrypt the data
                it.otrKey = generateRandomAES256Key().data
                // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
                it.assetDataPath?.let { assetDataPath ->
                    assetDataSource.uploadAndPersistPrivateAsset(it.mimeType, assetDataPath, AES256Key(it.otrKey), null)
                        .onFailure { failure ->
                            // on upload failure we still want link previews being included without image
                            kaliumLogger.e("Upload of link preview asset failed: $failure")
                        }.getOrNull()?.let { (assetId, sha256Key) ->
                            it.assetToken = assetId.assetToken ?: ""
                            it.assetKey = assetId.key
                            it.assetDomain = assetId.domain
                            it.sha256Key = sha256Key.data
                            it
                        }
                }
            }
            linkPreview.copy(image = imageCopy)
        }
    }
}

fun AttachmentDraft.metadata(): AssetContent.AssetMetadata? {

    val type = AttachmentType.fromMimeTypeString(mimeType)

    return when (type) {
        AttachmentType.IMAGE -> AssetContent.AssetMetadata.Image(assetWidth ?: 0, assetHeight ?: 0)

        AttachmentType.AUDIO -> AssetContent.AssetMetadata.Audio(
            durationMs = assetDuration ?: 0,
            normalizedLoudness = null,
        )

        AttachmentType.VIDEO -> AssetContent.AssetMetadata.Video(
            width = assetWidth ?: 0,
            height = assetHeight ?: 0,
            durationMs = assetDuration ?: 0
        )

        AttachmentType.GENERIC_FILE -> {
            if (mimeType == "application/pdf") {
                AssetContent.AssetMetadata.Image(
                    width = assetWidth ?: 0,
                    height = assetHeight ?: 0,
                )
            } else {
                null
            }
        }
    }
}
