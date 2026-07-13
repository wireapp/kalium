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

internal interface MessageEntryPoints {
    val messageRepository: MessageRepository
    val dispatcher: KaliumDispatcher
    val messageSendFailureHandler: MessageSendFailureHandler
    val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler
    val confirmationDeliveryHandler: ConfirmationDeliveryHandler
    val enqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase
    val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
    val messageSender: MessageSender
    val persistMessage: PersistMessageUseCase
    val sendTextMessage: SendTextMessageUseCase
    val sendMultipartMessage: SendMultipartMessageUseCase
    val sendEditTextMessage: SendEditTextMessageUseCase
    val sendEditMultipartMessage: SendEditMultipartMessageUseCase
    val getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase
    val sendPendingAssetMessage: SendPendingAssetMessageUseCase
    val retryFailedMessage: RetryFailedMessageUseCase
    val getMessageById: GetMessageByIdUseCase
    val observeMessageById: ObserveMessageByIdUseCase
    val sendAssetMessage: ScheduleNewAssetMessageUseCase
    val getAssetMessage: GetMessageAssetUseCase
    val getImageAssetMessagesByConversation: GetImageAssetMessagesForConversationUseCase
    val getRecentMessages: GetRecentMessagesUseCase
    val deleteMessage: DeleteMessageUseCase
    val toggleReaction: ToggleReactionUseCase
    val observeMessageReactions: ObserveMessageReactionsUseCase
    val observeMessageReceipts: ObserveMessageReceiptsUseCase
    val sendKnock: SendKnockUseCase
    val sendLocation: SendLocationUseCase
    val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase
    val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase
    val getNotifications: GetNotificationsUseCase
    val sendConfirmation: SendConfirmationUseCase
    val resetSession: ResetSessionUseCase
    val sendButtonActionConfirmationMessage: SendButtonActionConfirmationMessageUseCase
    val sendButtonActionMessage: SendButtonActionMessageUseCase

    @OptIn(InternalKaliumApi::class)
    val sendButtonMessage: SendButtonMessageUseCase

    val getSearchedConversationMessagePosition: GetSearchedConversationMessagePositionUseCase
    val searchMessagesInConversation: SearchMessagesInConversationUseCase
    val searchMessagesGlobally: SearchMessagesGloballyUseCase
    val observeAssetStatuses: ObserveAssetStatusesUseCase
    val saveMessageDraftUseCase: SaveMessageDraftUseCase
    val getMessageDraftUseCase: GetMessageDraftUseCase
    val removeMessageDraftUseCase: RemoveMessageDraftUseCase
    val sendInCallReactionUseCase: SendInCallReactionUseCase
    val getSenderNameByMessageId: GetSenderNameByMessageIdUseCase
    val getNextAudioMessageInConversation: GetNextAudioMessageInConversationUseCase
    val observeAssetUploadState: ObserveAssetUploadStateUseCase
    val updateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase
}
