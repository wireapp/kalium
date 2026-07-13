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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepositoryImpl
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.ObservePendingConnectionRequestsUseCase
import com.wire.kalium.logic.feature.conversation.apps.ChangeAccessForAppsInConversationUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateChannelUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.GroupConversationCreator
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationLocallyUseCase
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
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCase
import com.wire.kalium.util.KaliumDispatcher

internal interface ConversationEntryPoints {
    val conversationRepository: ConversationRepository
    val callRepository: CallRepository
    val messageRepository: MessageRepository
    val assetRepository: AssetRepository
    val dispatcher: KaliumDispatcher
    val getConversations: GetConversationsUseCase
    val getConversationDetails: GetConversationUseCase
    val getOneToOneConversation: GetOneToOneConversationDetailsUseCase
    val observeConversationListDetails: ObserveConversationListDetailsUseCase
    val observeConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase
    val observeConversationMembers: ObserveConversationMembersUseCase
    val getMembersToMention: MembersToMentionUseCase
    val observeEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase
    val observeUserListById: ObserveUserListByIdUseCase
    val observeConversationDetails: ObserveConversationDetailsUseCase
    val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
    val notifyConversationIsOpen: NotifyConversationIsOpenUseCase
    val observeIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
    val observeConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
    val deleteTeamConversation: DeleteTeamConversationUseCase
    val createGroupConversation: GroupConversationCreator
    val createRegularGroup: CreateRegularGroupUseCase
    val createChannel: CreateChannelUseCase
    val addMemberToConversationUseCase: AddMemberToConversationUseCase
    val addServiceToConversationUseCase: AddServiceToConversationUseCase
    val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
    val isOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
    val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
    val updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
    val observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase
    val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
    val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
    val markConversationAsReadLocally: MarkConversationAsReadLocallyUseCase
    val updateConversationAccess: UpdateConversationAccessRoleUseCase
    val changeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase
    val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
    val removeMemberFromConversation: RemoveMemberFromConversationUseCase
    val leaveConversation: LeaveConversationUseCase
    val promoteAdminAndLeaveConversation: PromoteAdminAndLeaveConversationUseCase
    val checkConversationLeaveConditions: CheckConversationLeaveConditionsUseCase
    val renameConversation: RenameConversationUseCase
    val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
    val clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase
    val clearConversationContent: ClearConversationContentUseCase
    val markConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase
    val deleteConversationLocallyUseCase: DeleteConversationLocallyUseCase
    val joinConversationViaCode: JoinConversationViaCodeUseCase
    val checkIConversationInviteCode: CheckConversationInviteCodeUseCase
    val updateConversationReceiptMode: UpdateConversationReceiptModeUseCase
    val generateGuestRoomLink: GenerateGuestRoomLinkUseCase
    val revokeGuestRoomLink: RevokeGuestRoomLinkUseCase
    val observeGuestRoomLink: ObserveGuestRoomLinkUseCase
    val updateMessageTimer: UpdateMessageTimerUseCase
    val getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
    val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
    val canCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
    val observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
    val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl
    val typingIndicatorOutgoingRepository: TypingIndicatorOutgoingRepositoryImpl
    val sendTypingEvent: SendTypingEventUseCase
    val observeUsersTyping: ObserveUsersTypingUseCase
    val clearUsersTypingEvents: ClearUsersTypingEventsUseCase
    val setUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
    val observeInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
    val setNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
    val observeConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
    val syncConversationCode: SyncConversationCodeUseCase
    val observeConversationsFromFolder: ObserveConversationsFromFolderUseCase
    val getFavoriteFolder: GetFavoriteFolderUseCase
    val addConversationToFavorites: AddConversationToFavoritesUseCase
    val removeConversationFromFavorites: RemoveConversationFromFavoritesUseCase
    val observeUserFolders: ObserveUserFoldersUseCase
    val moveConversationToFolder: MoveConversationToFolderUseCase
    val removeConversationFromFolder: RemoveConversationFromFolderUseCase
    val createConversationFolder: CreateConversationFolderUseCase
    val isSelfUserViewerOnConversation: IsSelfUserViewerOnConversationUseCase
}
