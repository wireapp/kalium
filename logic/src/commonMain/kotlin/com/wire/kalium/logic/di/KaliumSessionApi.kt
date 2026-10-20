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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.asset.DeleteAssetUseCase
import com.wire.kalium.logic.feature.asset.GetAssetSizeLimitUseCase
import com.wire.kalium.logic.feature.asset.GetAvatarAssetUseCase
import com.wire.kalium.logic.feature.asset.GetImageAssetMessagesForConversationUseCase
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.GetPaginatedFlowOfAssetMessageByConversationIdUseCase
import com.wire.kalium.logic.feature.asset.ObserveAssetStatusesUseCase
import com.wire.kalium.logic.feature.asset.ObservePaginatedAssetImageMessages
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.upload.ScheduleNewAssetMessageUseCase
import com.wire.kalium.logic.feature.channels.ObserveChannelsCreationPermissionUseCase
import com.wire.kalium.logic.feature.client.ClientFingerprintUseCase
import com.wire.kalium.logic.feature.client.DeleteClientUseCase
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FinalizeMLSClientAfterE2EIEnrollmentUseCase
import com.wire.kalium.logic.feature.client.GetOrRegisterClientUseCase
import com.wire.kalium.logic.feature.client.IsProfileQRCodeEnabledUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.NeedsToRegisterClientUseCase
import com.wire.kalium.logic.feature.client.ObserveClientDetailsUseCase
import com.wire.kalium.logic.feature.client.ObserveClientsByUserIdUseCase
import com.wire.kalium.logic.feature.client.ObserveCurrentClientIdUseCase
import com.wire.kalium.logic.feature.client.ObserveIsWireCellsEnabledUseCase
import com.wire.kalium.logic.feature.client.UpdateClientVerificationStatusUseCase
import com.wire.kalium.logic.feature.conversation.AddMemberToConversationUseCase
import com.wire.kalium.logic.feature.conversation.AddServiceToConversationUseCase
import com.wire.kalium.logic.feature.conversation.CheckConversationLeaveConditionsUseCase
import com.wire.kalium.logic.feature.conversation.CheckOneToOneConversationIsReadyUseCase
import com.wire.kalium.logic.feature.conversation.ClearConversationContentUseCase
import com.wire.kalium.logic.feature.conversation.ClearUsersTypingEventsUseCase
import com.wire.kalium.logic.feature.conversation.GetAllContactsNotInConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationUnreadEventsCountUseCase
import com.wire.kalium.logic.feature.conversation.GetOneToOneConversationDetailsUseCase
import com.wire.kalium.logic.feature.conversation.GetOrCreateOneToOneConversationUseCase
import com.wire.kalium.logic.feature.conversation.GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase
import com.wire.kalium.logic.feature.conversation.GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase
import com.wire.kalium.logic.feature.conversation.IsOneToOneConversationCreatedUseCase
import com.wire.kalium.logic.feature.conversation.IsSelfUserViewerOnConversationUseCase
import com.wire.kalium.logic.feature.conversation.JoinConversationViaCodeUseCase
import com.wire.kalium.logic.feature.conversation.LeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.MarkConversationAsReadLocallyUseCase
import com.wire.kalium.logic.feature.conversation.MembersToMentionUseCase
import com.wire.kalium.logic.feature.conversation.NotifyConversationIsOpenUseCase
import com.wire.kalium.logic.feature.conversation.ObserveArchivedUnreadConversationsCountUseCase
import com.wire.kalium.logic.feature.conversation.ObserveConversationInteractionAvailabilityUseCase
import com.wire.kalium.logic.feature.conversation.ObserveConversationListDetailsUseCase
import com.wire.kalium.logic.feature.conversation.ObserveConversationListDetailsWithEventsUseCase
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.feature.conversation.ObserveConversationUnderLegalHoldNotifiedUseCase
import com.wire.kalium.logic.feature.conversation.ObserveDegradedConversationNotifiedUseCase
import com.wire.kalium.logic.feature.conversation.ObserveEligibleMembersForConversationAdminRoleUseCase
import com.wire.kalium.logic.feature.conversation.ObserveIsSelfUserMemberUseCase
import com.wire.kalium.logic.feature.conversation.ObserveUserListByIdUseCase
import com.wire.kalium.logic.feature.conversation.ObserveUsersTypingUseCase
import com.wire.kalium.logic.feature.conversation.PromoteAdminAndLeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
import com.wire.kalium.logic.feature.conversation.RenameConversationUseCase
import com.wire.kalium.logic.feature.conversation.SendTypingEventUseCase
import com.wire.kalium.logic.feature.conversation.SetNotifiedAboutConversationUnderLegalHoldUseCase
import com.wire.kalium.logic.feature.conversation.SetUserInformedAboutVerificationUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationCodeUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationAccessRoleUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationArchivedStatusUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationMemberRoleUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationMutedStatusUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationReadDateUseCase
import com.wire.kalium.logic.feature.conversation.UpdateConversationReceiptModeUseCase
import com.wire.kalium.logic.feature.conversation.apps.ChangeAccessForAppsInConversationUseCase
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelAddPermissionUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateChannelUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase
import com.wire.kalium.logic.feature.conversation.delete.MarkConversationAsDeletedLocallyUseCase
import com.wire.kalium.logic.feature.conversation.folder.AddConversationToFavoritesUseCase
import com.wire.kalium.logic.feature.conversation.folder.CreateConversationFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.MoveConversationToFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.ObserveConversationsFromFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.ObserveUserFoldersUseCase
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFavoritesUseCase
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFolderUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.CanCreatePasswordProtectedLinksUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.GenerateGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.RevokeGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCase
import com.wire.kalium.logic.feature.debug.BreakSessionUseCase
import com.wire.kalium.logic.feature.debug.ChangeProfilingUseCase
import com.wire.kalium.logic.feature.debug.DebugFeedConversationUseCase
import com.wire.kalium.logic.feature.debug.DisableEventProcessingUseCase
import com.wire.kalium.logic.feature.debug.GetConversationCryptoStatsUseCase
import com.wire.kalium.logic.feature.debug.GetConversationEpochFromCCUseCase
import com.wire.kalium.logic.feature.debug.GetCryptoServiceReportUseCase
import com.wire.kalium.logic.feature.debug.GetDebugE2EICertificateExpirationUseCase
import com.wire.kalium.logic.feature.debug.GetFeatureConfigUseCase
import com.wire.kalium.logic.feature.debug.GetSqlCipherVersionUseCase
import com.wire.kalium.logic.feature.debug.ObserveDatabaseLoggerStateUseCase
import com.wire.kalium.logic.feature.debug.ObserveDebugCRLExpirationAfterOneMinuteUseCase
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCase
import com.wire.kalium.logic.feature.debug.SetDebugCRLExpirationAfterOneMinuteUseCase
import com.wire.kalium.logic.feature.debug.SetDebugE2EICertificateExpirationUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetMembersE2EICertificateStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.GetUserMlsClientIdentitiesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.IsOtherUserE2EIVerifiedUseCase
import com.wire.kalium.logic.feature.incallreaction.SendInCallReactionUseCase
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.meeting.CreateNewMeetingUseCase
import com.wire.kalium.logic.feature.meeting.DeleteMeetingForEveryoneUseCase
import com.wire.kalium.logic.feature.meeting.DeleteMeetingForMeUseCase
import com.wire.kalium.logic.feature.meeting.EnsureMeetingIsMLSEstablishedUseCase
import com.wire.kalium.logic.feature.meeting.GetNextUnfinishedMeetingOccurrenceUseCase
import com.wire.kalium.logic.feature.meeting.GetPaginatedMeetingOccurrencesUseCase
import com.wire.kalium.logic.feature.meeting.ObserveMeetingOccurrenceUseCase
import com.wire.kalium.logic.feature.meeting.UpdateMeetingUseCase
import com.wire.kalium.logic.feature.message.DeleteMessageUseCase
import com.wire.kalium.logic.feature.message.FetchOlderNomadMessagesByConversationUseCase
import com.wire.kalium.logic.feature.message.GetNotificationsUseCase
import com.wire.kalium.logic.feature.message.GetPaginatedFlowOfMessagesByConversationUseCase
import com.wire.kalium.logic.feature.message.GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase
import com.wire.kalium.logic.feature.message.GetSearchedConversationMessagePositionUseCase
import com.wire.kalium.logic.feature.message.GetSenderNameByMessageIdUseCase
import com.wire.kalium.logic.feature.message.MarkMessagesAsNotifiedUseCase
import com.wire.kalium.logic.feature.message.ObserveMessageReactionsUseCase
import com.wire.kalium.logic.feature.message.ObserveMessageReceiptsUseCase
import com.wire.kalium.logic.feature.message.RetryFailedMessageUseCase
import com.wire.kalium.logic.feature.message.SendEditMultipartMessageUseCase
import com.wire.kalium.logic.feature.message.SendEditTextMessageUseCase
import com.wire.kalium.logic.feature.message.SendKnockUseCase
import com.wire.kalium.logic.feature.message.SendLocationUseCase
import com.wire.kalium.logic.feature.message.SendMultipartMessageUseCase
import com.wire.kalium.logic.feature.message.SendTextMessageUseCase
import com.wire.kalium.logic.feature.message.ToggleReactionUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.feature.message.draft.GetMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.RemoveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.draft.SaveMessageDraftUseCase
import com.wire.kalium.logic.feature.message.ephemeral.EnqueueMessageSelfDeletionUseCase
import com.wire.kalium.logic.feature.message.linkpreview.DetectLinkPreviewTargetUseCase
import com.wire.kalium.logic.feature.message.linkpreview.GenerateLinkPreviewUseCase
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenUseCase
import com.wire.kalium.logic.feature.personaltoteamaccount.CanMigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.publicuser.GetAllContactsUseCase
import com.wire.kalium.logic.feature.publicuser.GetKnownUserUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.search.FederatedSearchParser
import com.wire.kalium.logic.feature.search.IsFederationSearchAllowedUseCase
import com.wire.kalium.logic.feature.search.SearchUsersByHandleUseCase
import com.wire.kalium.logic.feature.search.SearchUsersByNameUseCase
import com.wire.kalium.logic.feature.sessionreset.ResetSessionUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCase
import com.wire.kalium.logic.feature.user.DeleteAccountUseCase
import com.wire.kalium.logic.feature.user.GetSelfTeamIdUseCase
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.feature.user.GetUserInfoUseCase
import com.wire.kalium.logic.feature.user.IsPasswordRequiredUseCase
import com.wire.kalium.logic.feature.user.IsReadOnlyAccountUseCase
import com.wire.kalium.logic.feature.user.ObserveSelfUserUseCase
import com.wire.kalium.logic.feature.user.ObserveSelfUserWithTeamUseCase
import com.wire.kalium.logic.feature.user.ObserveUserInfoUseCase
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase
import com.wire.kalium.logic.feature.user.SetUserHandleUseCase
import com.wire.kalium.logic.feature.user.UpdateAccentColorUseCase
import com.wire.kalium.logic.feature.user.UpdateDisplayNameUseCase
import com.wire.kalium.logic.feature.user.UpdateEmailUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfAvailabilityStatusUseCase
import com.wire.kalium.logic.feature.user.UploadUserAvatarUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.ObserveLinkPreviewsEnabledUseCase
import com.wire.kalium.logic.feature.user.linkPreviews.PersistLinkPreviewsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCase
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.ObserveTypingIndicatorEnabledUseCase
import com.wire.kalium.logic.feature.user.typingIndicator.PersistTypingIndicatorStatusConfigUseCase
import com.wire.kalium.logic.sync.ForegroundActionsUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.util.DebugKaliumApi

/**
 * Individual conversation bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumConversationApi {
    public val conversationGetOneToOneConversation: GetOneToOneConversationDetailsUseCase
    public val conversationObserveConversationListDetails: ObserveConversationListDetailsUseCase
    public val conversationObserveConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase
    public val conversationObserveConversationMembers: ObserveConversationMembersUseCase
    public val conversationGetMembersToMention: MembersToMentionUseCase
    public val conversationObserveEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase
    public val conversationObserveUserListById: ObserveUserListByIdUseCase
    public val conversationGetConversationProtocolInfo: GetConversationProtocolInfoUseCase
    public val conversationNotifyConversationIsOpen: NotifyConversationIsOpenUseCase
    public val conversationObserveIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
    public val conversationObserveConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
    public val conversationDeleteTeamConversation: DeleteTeamConversationUseCase
    public val conversationCreateRegularGroup: CreateRegularGroupUseCase
    public val conversationCreateChannel: CreateChannelUseCase
    public val conversationAddMemberToConversationUseCase: AddMemberToConversationUseCase
    public val conversationAddServiceToConversationUseCase: AddServiceToConversationUseCase
    public val conversationGetOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
    public val conversationCheckOneToOneConversationIsReadyUseCase: CheckOneToOneConversationIsReadyUseCase
    public val conversationIsOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
    public val conversationUpdateConversationMutedStatus: UpdateConversationMutedStatusUseCase
    public val conversationUpdateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
    public val conversationUpdateConversationReadDateUseCase: UpdateConversationReadDateUseCase
    public val conversationMarkConversationAsReadLocally: MarkConversationAsReadLocallyUseCase
    public val conversationUpdateConversationAccess: UpdateConversationAccessRoleUseCase
    public val conversationChangeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase
    public val conversationUpdateConversationMemberRole: UpdateConversationMemberRoleUseCase
    public val conversationRemoveMemberFromConversation: RemoveMemberFromConversationUseCase
    public val conversationLeaveConversation: LeaveConversationUseCase
    public val conversationPromoteAdminAndLeaveConversation: PromoteAdminAndLeaveConversationUseCase
    public val conversationCheckConversationLeaveConditions: CheckConversationLeaveConditionsUseCase
    public val conversationRenameConversation: RenameConversationUseCase
    public val conversationClearConversationContent: ClearConversationContentUseCase
    public val conversationMarkConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase
    public val conversationJoinConversationViaCode: JoinConversationViaCodeUseCase
    public val conversationUpdateConversationReceiptMode: UpdateConversationReceiptModeUseCase
    public val conversationGenerateGuestRoomLink: GenerateGuestRoomLinkUseCase
    public val conversationRevokeGuestRoomLink: RevokeGuestRoomLinkUseCase
    public val conversationObserveGuestRoomLink: ObserveGuestRoomLinkUseCase
    public val conversationUpdateMessageTimer: UpdateMessageTimerUseCase
    public val conversationGetConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
    public val conversationRefreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
    public val conversationCanCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
    public val conversationObserveArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
    public val conversationSendTypingEvent: SendTypingEventUseCase
    public val conversationObserveUsersTyping: ObserveUsersTypingUseCase
    public val conversationClearUsersTypingEvents: ClearUsersTypingEventsUseCase
    public val conversationSetUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
    public val conversationObserveInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
    public val conversationSetNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
    public val conversationObserveConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
    public val conversationSyncConversationCode: SyncConversationCodeUseCase
    public val conversationObserveConversationsFromFolder: ObserveConversationsFromFolderUseCase
    public val conversationGetFavoriteFolder: GetFavoriteFolderUseCase
    public val conversationAddConversationToFavorites: AddConversationToFavoritesUseCase
    public val conversationRemoveConversationFromFavorites: RemoveConversationFromFavoritesUseCase
    public val conversationObserveUserFolders: ObserveUserFoldersUseCase
    public val conversationMoveConversationToFolder: MoveConversationToFolderUseCase
    public val conversationRemoveConversationFromFolder: RemoveConversationFromFolderUseCase
    public val conversationCreateConversationFolder: CreateConversationFolderUseCase
    public val conversationIsSelfUserViewerOnConversation: IsSelfUserViewerOnConversationUseCase
    public val conversationGetPaginatedFlowOfConversationDetailsWithEventsBySearchQuery:
        GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase
    public val conversationGetPaginatedFlowOfConversationDetailsWithMembersBySearchQuery:
        GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase
}

/**
 * Individual message bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumMessageApi {
    public val messageEnqueueMessageSelfDeletion: EnqueueMessageSelfDeletionUseCase
    public val messageSendTextMessage: SendTextMessageUseCase
    public val messageSendMultipartMessage: SendMultipartMessageUseCase
    public val messageSendEditTextMessage: SendEditTextMessageUseCase
    public val messageSendEditMultipartMessage: SendEditMultipartMessageUseCase
    public val messageRetryFailedMessage: RetryFailedMessageUseCase
    public val messageSendAssetMessage: ScheduleNewAssetMessageUseCase
    public val messageGetAssetMessage: GetMessageAssetUseCase
    public val messageGetImageAssetMessagesByConversation: GetImageAssetMessagesForConversationUseCase
    public val messageDeleteMessage: DeleteMessageUseCase
    public val messageToggleReaction: ToggleReactionUseCase
    public val messageObserveMessageReactions: ObserveMessageReactionsUseCase
    public val messageObserveMessageReceipts: ObserveMessageReceiptsUseCase
    public val messageSendKnock: SendKnockUseCase
    public val messageSendLocation: SendLocationUseCase
    public val messageMarkMessagesAsNotified: MarkMessagesAsNotifiedUseCase
    public val messageUpdateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase
    public val messageGetNotifications: GetNotificationsUseCase
    public val messageResetSession: ResetSessionUseCase
    public val messageSendButtonActionMessage: SendButtonActionMessageUseCase
    public val messageGetSearchedConversationMessagePosition: GetSearchedConversationMessagePositionUseCase
    public val messageObserveAssetStatuses: ObserveAssetStatusesUseCase
    public val messageSaveMessageDraftUseCase: SaveMessageDraftUseCase
    public val messageGetMessageDraftUseCase: GetMessageDraftUseCase
    public val messageRemoveMessageDraftUseCase: RemoveMessageDraftUseCase
    public val messageSendInCallReactionUseCase: SendInCallReactionUseCase
    public val messageGetSenderNameByMessageId: GetSenderNameByMessageIdUseCase
    public val messageUpdateAudioMessageNormalizedLoudnessUseCase: UpdateAudioMessageNormalizedLoudnessUseCase
    public val messageGenerateLinkPreview: GenerateLinkPreviewUseCase
    public val messageDetectLinkPreviewTarget: DetectLinkPreviewTargetUseCase
    public val messageGetPaginatedFlowOfMessagesByConversation: GetPaginatedFlowOfMessagesByConversationUseCase
    public val messageGetPaginatedFlowOfMessagesBySearchQueryAndConversation:
        GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase
    public val messageGetPaginatedFlowOfAssetMessageByConversationId: GetPaginatedFlowOfAssetMessageByConversationIdUseCase
    public val messageObservePaginatedImageAssetMessageByConversationId: ObservePaginatedAssetImageMessages
    public val messageFetchOlderMessagesByConversationId: FetchOlderNomadMessagesByConversationUseCase
}

/**
 * Individual client bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumClientApi {
    public val clientFetchSelfClients: FetchSelfClientsFromRemoteUseCase
    public val clientFetchUsersClients: FetchUsersClientsFromRemoteUseCase
    public val clientGetOtherUserClients: ObserveClientsByUserIdUseCase
    public val clientObserveClientDetailsUseCase: ObserveClientDetailsUseCase
    public val clientDeleteClient: DeleteClientUseCase
    public val clientNeedsToRegisterClient: NeedsToRegisterClientUseCase
    public val clientMlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase
    public val clientRestartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
    public val clientObserveCurrentClientId: ObserveCurrentClientIdUseCase
    public val clientGetOrRegister: GetOrRegisterClientUseCase
    public val clientRemoteClientFingerPrint: ClientFingerprintUseCase
    public val clientUpdateClientVerificationStatus: UpdateClientVerificationStatusUseCase
}

/**
 * Individual user bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumUserApi {
    public val userRefreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
    public val userGetSelfUser: GetSelfUserUseCase
    public val userGetSelfTeamId: GetSelfTeamIdUseCase
    public val userObserveSelfUser: ObserveSelfUserUseCase
    public val userObserveSelfUserWithTeam: ObserveSelfUserWithTeamUseCase
    public val userObserveUserInfo: ObserveUserInfoUseCase
    public val userUploadUserAvatar: UploadUserAvatarUseCase
    public val userGetPublicAsset: GetAvatarAssetUseCase
    public val userFinalizeMLSClientAfterE2EIEnrollment: FinalizeMLSClientAfterE2EIEnrollmentUseCase
    public val userGetE2EICertificate: GetMLSClientIdentityUseCase
    public val userGetUserE2eiCertificateStatus: IsOtherUserE2EIVerifiedUseCase
    public val userGetUserMlsClientIdentities: GetUserMlsClientIdentitiesUseCase
    public val userGetMembersE2EICertificateStatuses: GetMembersE2EICertificateStatusesUseCase
    public val userDeleteAsset: DeleteAssetUseCase
    public val userSetUserHandle: SetUserHandleUseCase
    public val userGetAllKnownUsers: GetAllContactsUseCase
    public val userGetKnownUser: GetKnownUserUseCase
    public val userGetUserInfo: GetUserInfoUseCase
    public val userUpdateSelfAvailabilityStatus: UpdateSelfAvailabilityStatusUseCase
    public val userGetAllContactsNotInConversation: GetAllContactsNotInConversationUseCase
    public val userIsPasswordRequired: IsPasswordRequiredUseCase
    public val userIsReadOnlyAccount: IsReadOnlyAccountUseCase
    public val userObserveReadReceiptsEnabled: ObserveReadReceiptsEnabledUseCase
    public val userObserveTypingIndicatorEnabled: ObserveTypingIndicatorEnabledUseCase
    public val userObserveLinkPreviewsEnabled: ObserveLinkPreviewsEnabledUseCase
    public val userPersistReadReceiptsStatusConfig: PersistReadReceiptsStatusConfigUseCase
    public val userPersistTypingIndicatorStatusConfig: PersistTypingIndicatorStatusConfigUseCase
    public val userPersistLinkPreviewsStatusConfig: PersistLinkPreviewsStatusConfigUseCase
    public val userServerLinks: SelfServerConfigUseCase
    public val userUpdateDisplayName: UpdateDisplayNameUseCase
    public val userUpdateAccentColor: UpdateAccentColorUseCase
    public val userUpdateEmail: UpdateEmailUseCase
    public val userGetAssetSizeLimit: GetAssetSizeLimitUseCase
    public val userDeleteAccount: DeleteAccountUseCase
    public val userIsPersonalToTeamAccountSupportedByBackend: CanMigrateFromPersonalToTeamUseCase
    public val userForegroundActions: ForegroundActionsUseCase
    public val userIsWireCellsEnabled: IsWireCellsEnabledUseCase
    public val userObserveIsWireCellsEnabled: ObserveIsWireCellsEnabledUseCase
    public val userIsWireCellsEnabledForConversation: IsWireCellsEnabledForConversationUseCase
    public val userIsProfileQRCodeEnabled: IsProfileQRCodeEnabledUseCase
}

/**
 * Individual search bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumSearchApi {
    public val searchSearchUsersByName: SearchUsersByNameUseCase
    public val searchSearchUsersByHandle: SearchUsersByHandleUseCase
    public val searchFederatedSearchParser: FederatedSearchParser
    public val searchIsFederationSearchAllowedUseCase: IsFederationSearchAllowedUseCase
}

/**
 * Individual channels bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumChannelsApi {
    public val channelsObserveChannelsCreationPermissionUseCase: ObserveChannelsCreationPermissionUseCase
    public val channelsUpdateChannelAddPermission: UpdateChannelAddPermissionUseCase
}

/**
 * Individual meeting bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
public interface KaliumMeetingApi {
    public val meetingGetPaginatedMeetingOccurrenceDetails: GetPaginatedMeetingOccurrencesUseCase
    public val meetingObserveMeetingOccurrence: ObserveMeetingOccurrenceUseCase
    public val meetingDeleteMeetingForEveryone: DeleteMeetingForEveryoneUseCase
    public val meetingDeleteMeetingForMe: DeleteMeetingForMeUseCase
    public val meetingGetNextUnfinishedMeetingOccurrence: GetNextUnfinishedMeetingOccurrenceUseCase
    public val meetingEnsureMeetingIsMLSEstablished: EnsureMeetingIsMLSEstablishedUseCase
    public val meetingCreateNewMeeting: CreateNewMeetingUseCase
    public val meetingUpdateMeeting: UpdateMeetingUseCase
}

/**
 * Individual debug bindings from the active user generation.
 *
 * Accessors preserve the compatibility facade's fresh/cached use cases and captured state.
 * The session owns these bindings; obtaining this API creates no consumer owner. Callers must
 * quiesce legacy operations before account shutdown. Use [KaliumConsumer] for its explicitly
 * guarded, consumer-owned paths; parent-built use cases cannot capture child repositories.
 */
@DebugKaliumApi("Debug-only individual bindings for developer tooling, QA, and profiling.")
public interface KaliumDebugApi {
    public val debugGetSqlCipherVersion: GetSqlCipherVersionUseCase
    public val debugBreakSession: BreakSessionUseCase
    public val debugDisableEventProcessing: DisableEventProcessingUseCase
    public val debugGetConversationEpochFromCC: GetConversationEpochFromCCUseCase
    public val debugSendFCMTokenToServer: SendFCMTokenUseCase
    public val debugChangeProfiling: ChangeProfilingUseCase
    public val debugObserveDatabaseLoggerState: ObserveDatabaseLoggerStateUseCase
    public val debugDebugFeedConversationUseCase: DebugFeedConversationUseCase
    public val debugGetFeatureConfig: GetFeatureConfigUseCase
    public val debugGetCryptoServiceReport: GetCryptoServiceReportUseCase
    public val debugGetDebugE2EICertificateExpiration: GetDebugE2EICertificateExpirationUseCase
    public val debugSetDebugE2EICertificateExpiration: SetDebugE2EICertificateExpirationUseCase
    public val debugObserveDebugCRLExpirationAfterOneMinute: ObserveDebugCRLExpirationAfterOneMinuteUseCase
    public val debugSetDebugCRLExpirationAfterOneMinute: SetDebugCRLExpirationAfterOneMinuteUseCase
    public val debugRepairFaultyRemovalKeysUseCase: RepairFaultyRemovalKeysUseCase
    public val debugGetConversationCryptoStats: GetConversationCryptoStatsUseCase
}
