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

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.GetImageAssetMessagesForConversationUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.ObserveAssetStatusesUseCase
import com.wire.kalium.logic.feature.asset.ObserveAssetUploadStateUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.incallreaction.SendInCallReactionUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionConfirmationMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonMessageUseCase
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.draft.GetMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.RemoveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.SaveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCase
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCase
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher

/** Public message API facade backed entirely by the user-session dependency graph. */
public class MessageScope internal constructor(
    private val entryPoints: MessageEntryPoints,
) {
    internal val messageRepository: MessageRepository get() = entryPoints.messageRepository
    internal val dispatcher: KaliumDispatcher get() = entryPoints.dispatcher
    internal val messageSendFailureHandler: MessageSendFailureHandler get() = entryPoints.messageSendFailureHandler
    internal val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler
        get() = entryPoints.ephemeralMessageDeletionHandler
    internal val confirmationDeliveryHandler: ConfirmationDeliveryHandler get() = entryPoints.confirmationDeliveryHandler
    public val enqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase
        get() = entryPoints.enqueueMessageSelfDeletion
    public val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
        get() = entryPoints.deleteEphemeralMessageEndDate
    internal val messageSender: MessageSender get() = entryPoints.messageSender
    internal val persistMessage: PersistMessageUseCase get() = entryPoints.persistMessage
    public val sendTextMessage: SendTextMessageUseCase get() = entryPoints.sendTextMessage
    public val sendMultipartMessage: SendMultipartMessageUseCase get() = entryPoints.sendMultipartMessage
    public val sendEditTextMessage: SendEditTextMessageUseCase get() = entryPoints.sendEditTextMessage
    public val sendEditMultipartMessage: SendEditMultipartMessageUseCase get() = entryPoints.sendEditMultipartMessage
    internal val getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase
        get() = entryPoints.getAssetMessageTransferStatus
    internal val sendPendingAssetMessage: SendPendingAssetMessageUseCase get() = entryPoints.sendPendingAssetMessage
    public val retryFailedMessage: RetryFailedMessageUseCase get() = entryPoints.retryFailedMessage
    public val getMessageById: GetMessageByIdUseCase get() = entryPoints.getMessageById
    public val observeMessageById: ObserveMessageByIdUseCase get() = entryPoints.observeMessageById
    public val sendAssetMessage: ScheduleNewAssetMessageUseCase get() = entryPoints.sendAssetMessage
    public val getAssetMessage: GetMessageAssetUseCase get() = entryPoints.getAssetMessage
    public val getImageAssetMessagesByConversation: GetImageAssetMessagesForConversationUseCase
        get() = entryPoints.getImageAssetMessagesByConversation
    public val getRecentMessages: GetRecentMessagesUseCase get() = entryPoints.getRecentMessages
    public val deleteMessage: DeleteMessageUseCase get() = entryPoints.deleteMessage
    public val toggleReaction: ToggleReactionUseCase get() = entryPoints.toggleReaction
    public val observeMessageReactions: ObserveMessageReactionsUseCase get() = entryPoints.observeMessageReactions
    public val observeMessageReceipts: ObserveMessageReceiptsUseCase get() = entryPoints.observeMessageReceipts
    public val sendKnock: SendKnockUseCase get() = entryPoints.sendKnock
    public val sendLocation: SendLocationUseCase get() = entryPoints.sendLocation
    public val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase get() = entryPoints.markMessagesAsNotified
    public val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase
        get() = entryPoints.updateAssetMessageTransferStatus
    public val getNotifications: GetNotificationsUseCase get() = entryPoints.getNotifications
    internal val sendConfirmation: SendConfirmationUseCase get() = entryPoints.sendConfirmation
    public val resetSession: ResetSessionUseCase get() = entryPoints.resetSession
    public val sendButtonActionConfirmationMessage: SendButtonActionConfirmationMessageUseCase
        get() = entryPoints.sendButtonActionConfirmationMessage
    public val sendButtonActionMessage: SendButtonActionMessageUseCase get() = entryPoints.sendButtonActionMessage

    @OptIn(InternalKaliumApi::class)
    public val sendButtonMessage: SendButtonMessageUseCase get() = entryPoints.sendButtonMessage

    public val getSearchedConversationMessagePosition: GetSearchedConversationMessagePositionUseCase
        get() = entryPoints.getSearchedConversationMessagePosition
    public val searchMessagesInConversation: SearchMessagesInConversationUseCase
        get() = entryPoints.searchMessagesInConversation
    public val searchMessagesGlobally: SearchMessagesGloballyUseCase get() = entryPoints.searchMessagesGlobally
    public val observeAssetStatuses: ObserveAssetStatusesUseCase get() = entryPoints.observeAssetStatuses
    public val saveMessageDraftUseCase: SaveMessageDraftUseCase get() = entryPoints.saveMessageDraftUseCase
    public val getMessageDraftUseCase: GetMessageDraftUseCase get() = entryPoints.getMessageDraftUseCase
    public val removeMessageDraftUseCase: RemoveMessageDraftUseCase get() = entryPoints.removeMessageDraftUseCase
    public val sendInCallReactionUseCase: SendInCallReactionUseCase get() = entryPoints.sendInCallReactionUseCase
    public val getSenderNameByMessageId: GetSenderNameByMessageIdUseCase get() = entryPoints.getSenderNameByMessageId
    public val getNextAudioMessageInConversation: GetNextAudioMessageInConversationUseCase
        get() = entryPoints.getNextAudioMessageInConversation
    public val observeAssetUploadState: ObserveAssetUploadStateUseCase get() = entryPoints.observeAssetUploadState
    public val updateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase
        get() = entryPoints.updateAudioMessageNormalizedLoudnessUseCase
}
