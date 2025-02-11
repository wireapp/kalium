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
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
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
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
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

        val previews = uploadLinkPreviewImages(linkPreviews)

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
                date = Clock.System.now(),
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

    private suspend fun uploadLinkPreviewImages(linkPreviews: List<MessageLinkPreview>): List<MessageLinkPreview> {
        return linkPreviews.map { linkPreview ->
            val imageCopy = linkPreview.image?.let {
                // Generate the otr asymmetric key that will be used to encrypt the data
                it.otrKey = generateRandomAES256Key().data
                // The assetDataSource will encrypt the data with the provided otrKey and upload it if successful
                it.assetDataPath?.let { assetDataPath ->
                    assetDataSource.uploadAndPersistPrivateAsset(
                        it.mimeType,
                        assetDataPath,
                        AES256Key(it.otrKey),
                        null
                    ).onFailure { failure ->
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
