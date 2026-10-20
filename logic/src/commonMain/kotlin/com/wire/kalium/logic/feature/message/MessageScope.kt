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

import com.wire.kalium.logic.feature.asset.ObservePaginatedAssetImageMessages
import com.wire.kalium.logic.feature.asset.GetPaginatedFlowOfAssetMessageByConversationIdUseCase
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
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCaseImpl
import com.wire.kalium.logic.feature.incallreaction.SendInCallReactionUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionConfirmationMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonMessageUseCase
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCase
import com.wire.kalium.logic.feature.message.draft.GetMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.RemoveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.SaveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.message.linkpreview.DetectLinkPreviewTargetUseCase
import com.wire.kalium.logic.feature.message.linkpreview.GenerateLinkPreviewUseCase
import com.wire.kalium.logic.feature.message.poll.SendPollMessageUseCase
import com.wire.kalium.logic.feature.message.poll.SendPollVoteUseCase
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCase
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCase
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher

/** Compatibility facade over the individual bindings in the existing user graph. */
public class MessageScope internal constructor(private val api: LegacyMessageApi) {
    internal val messageRepository: MessageRepository
        get() = api.messageMessageRepository

    internal val dispatcher: KaliumDispatcher
        get() = api.messageDispatcher

    public val openMessageTimeline: OpenMessageTimelineUseCase
        get() = api.messageOpenMessageTimeline

    public val getPaginatedFlowOfMessageListItems: GetPaginatedFlowOfMessageListItemsUseCase
        get() = api.messageGetPaginatedFlowOfMessageListItems

    internal val messageSendFailureHandler: MessageSendFailureHandler
        get() = api.messageMessageSendFailureHandler

    internal val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler
        get() = api.messageEphemeralMessageDeletionHandler

    internal val sendDeliverSignalUseCase: SendDeliverSignalUseCase
        get() = api.messageSendDeliverSignalUseCase

    internal val confirmationDeliveryHandler: ConfirmationDeliveryHandler
        get() = api.messageConfirmationDeliveryHandler

    public val enqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase
        get() = api.messageEnqueueMessageSelfDeletion

    public val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
        get() = api.messageDeleteEphemeralMessageEndDate

    internal val messageSender: MessageSender
        get() = api.messageMessageSender

    internal val persistMessage: PersistMessageUseCase
        get() = api.messagePersistMessage

    public val sendTextMessage: SendTextMessageUseCase
        get() = api.messageSendTextMessage

    public val sendMultipartMessage: SendMultipartMessageUseCase
        get() = api.messageSendMultipartMessage

    public val sendEditTextMessage: SendEditTextMessageUseCase
        get() = api.messageSendEditTextMessage

    public val sendEditMultipartMessage: SendEditMultipartMessageUseCase
        get() = api.messageSendEditMultipartMessage

    internal val getAssetMessageTransferStatus: GetAssetMessageTransferStatusUseCase
        get() = api.messageGetAssetMessageTransferStatus

    internal val sendPendingAssetMessage: SendPendingAssetMessageUseCase
        get() = api.messageSendPendingAssetMessage

    public val retryFailedMessage: RetryFailedMessageUseCase
        get() = api.messageRetryFailedMessage

    public val getMessageById: GetMessageByIdUseCase
        get() = api.messageGetMessageById

    public val observeMessageById: ObserveMessageByIdUseCase
        get() = api.messageObserveMessageById

    public val sendAssetMessage: ScheduleNewAssetMessageUseCase
        get() = api.messageSendAssetMessage

    internal val scheduleAssetMessage: ScheduleNewAssetMessageUseCaseImpl
        get() = api.messageScheduleAssetMessage

    public val getAssetMessage: GetMessageAssetUseCase
        get() = api.messageGetAssetMessage

    public val getImageAssetMessagesByConversation: GetImageAssetMessagesForConversationUseCase
        get() = api.messageGetImageAssetMessagesByConversation

    public val getRecentMessages: GetRecentMessagesUseCase
        get() = api.messageGetRecentMessages

    public val deleteMessage: DeleteMessageUseCase
        get() = api.messageDeleteMessage

    public val toggleReaction: ToggleReactionUseCase
        get() = api.messageToggleReaction

    public val observeMessageReactions: ObserveMessageReactionsUseCase
        get() = api.messageObserveMessageReactions

    public val observeMessageReceipts: ObserveMessageReceiptsUseCase
        get() = api.messageObserveMessageReceipts

    public val sendKnock: SendKnockUseCase
        get() = api.messageSendKnock

    public val sendLocation: SendLocationUseCase
        get() = api.messageSendLocation

    public val markMessagesAsNotified: MarkMessagesAsNotifiedUseCase
        get() = api.messageMarkMessagesAsNotified

    public val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase
        get() = api.messageUpdateAssetMessageTransferStatus

    public val getNotifications: GetNotificationsUseCase
        get() = api.messageGetNotifications

    internal val sendConfirmation: SendConfirmationUseCase
        get() = api.messageSendConfirmation

    public val resetSession: ResetSessionUseCase
        get() = api.messageResetSession

    public val sendButtonActionConfirmationMessage: SendButtonActionConfirmationMessageUseCase
        get() = api.messageSendButtonActionConfirmationMessage

    public val sendButtonActionMessage: SendButtonActionMessageUseCase
        get() = api.messageSendButtonActionMessage

    @OptIn(InternalKaliumApi::class)
    public val sendButtonMessage: SendButtonMessageUseCase
        get() = api.messageSendButtonMessage

    public val sendPollMessage: SendPollMessageUseCase
        get() = api.messageSendPollMessage

    public val sendPollVote: SendPollVoteUseCase
        get() = api.messageSendPollVote

    public val getSearchedConversationMessagePosition: GetSearchedConversationMessagePositionUseCase
        get() = api.messageGetSearchedConversationMessagePosition

    public val searchMessagesInConversation: SearchMessagesInConversationUseCase
        get() = api.messageSearchMessagesInConversation

    public val searchMessagesGlobally: SearchMessagesGloballyUseCase
        get() = api.messageSearchMessagesGlobally

    public val observeAssetStatuses: ObserveAssetStatusesUseCase
        get() = api.messageObserveAssetStatuses

    public val saveMessageDraftUseCase: SaveMessageDraftUseCase
        get() = api.messageSaveMessageDraftUseCase

    public val getMessageDraftUseCase: GetMessageDraftUseCase
        get() = api.messageGetMessageDraftUseCase

    public val removeMessageDraftUseCase: RemoveMessageDraftUseCase
        get() = api.messageRemoveMessageDraftUseCase

    public val sendInCallReactionUseCase: SendInCallReactionUseCase
        get() = api.messageSendInCallReactionUseCase

    public val getSenderNameByMessageId: GetSenderNameByMessageIdUseCase
        get() = api.messageGetSenderNameByMessageId

    public val getNextAudioMessageInConversation: GetNextAudioMessageInConversationUseCase
        get() = api.messageGetNextAudioMessageInConversation

    public val observeAssetUploadState: ObserveAssetUploadStateUseCase
        get() = api.messageObserveAssetUploadState

    public val updateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase
        get() = api.messageUpdateAudioMessageNormalizedLoudnessUseCase

    public val generateLinkPreview: GenerateLinkPreviewUseCase
        get() = api.messageGenerateLinkPreview

    public val detectLinkPreviewTarget: DetectLinkPreviewTargetUseCase
        get() = api.messageDetectLinkPreviewTarget
    internal val bindingGetPaginatedFlowOfMessagesByConversation: GetPaginatedFlowOfMessagesByConversationUseCase
        get() = api.messageGetPaginatedFlowOfMessagesByConversation

    internal val bindingGetPaginatedFlowOfMessagesBySearchQueryAndConversation:
        GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase
        get() = api.messageGetPaginatedFlowOfMessagesBySearchQueryAndConversation

    internal val bindingGetPaginatedFlowOfAssetMessageByConversationId: GetPaginatedFlowOfAssetMessageByConversationIdUseCase
        get() = api.messageGetPaginatedFlowOfAssetMessageByConversationId

    internal val bindingObservePaginatedImageAssetMessageByConversationId: ObservePaginatedAssetImageMessages
        get() = api.messageObservePaginatedImageAssetMessageByConversationId

    internal val bindingFetchOlderMessagesByConversationId: FetchOlderNomadMessagesByConversationUseCase
        get() = api.messageFetchOlderMessagesByConversationId

}
