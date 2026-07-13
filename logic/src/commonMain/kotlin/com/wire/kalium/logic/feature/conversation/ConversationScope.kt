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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepositoryImpl
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
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

/** Public conversation API facade backed entirely by the user-session dependency graph. */
public class ConversationScope internal constructor(
    private val entryPoints: ConversationEntryPoints,
) {
    internal val conversationRepository: ConversationRepository get() = entryPoints.conversationRepository
    internal val callRepository: CallRepository get() = entryPoints.callRepository
    internal val messageRepository: MessageRepository get() = entryPoints.messageRepository
    internal val assetRepository: AssetRepository get() = entryPoints.assetRepository
    internal val dispatcher: KaliumDispatcher get() = entryPoints.dispatcher

    public val getConversations: GetConversationsUseCase get() = entryPoints.getConversations
    internal val getConversationDetails: GetConversationUseCase get() = entryPoints.getConversationDetails
    public val getOneToOneConversation: GetOneToOneConversationDetailsUseCase get() = entryPoints.getOneToOneConversation
    public val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = entryPoints.observeConversationListDetails
    public val observeConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase
        get() = entryPoints.observeConversationListDetailsWithEvents
    public val observeConversationMembers: ObserveConversationMembersUseCase get() = entryPoints.observeConversationMembers
    public val getMembersToMention: MembersToMentionUseCase get() = entryPoints.getMembersToMention
    public val observeEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase
        get() = entryPoints.observeEligibleMembersForConversationAdminRole
    public val observeUserListById: ObserveUserListByIdUseCase get() = entryPoints.observeUserListById
    public val observeConversationDetails: ObserveConversationDetailsUseCase get() = entryPoints.observeConversationDetails
    public val getConversationProtocolInfo: GetConversationProtocolInfoUseCase get() = entryPoints.getConversationProtocolInfo
    public val notifyConversationIsOpen: NotifyConversationIsOpenUseCase get() = entryPoints.notifyConversationIsOpen
    public val observeIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
        get() = entryPoints.observeIsSelfUserMemberUseCase
    public val observeConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
        get() = entryPoints.observeConversationInteractionAvailabilityUseCase
    public val deleteTeamConversation: DeleteTeamConversationUseCase get() = entryPoints.deleteTeamConversation
    internal val createGroupConversation: GroupConversationCreator get() = entryPoints.createGroupConversation
    public val createRegularGroup: CreateRegularGroupUseCase get() = entryPoints.createRegularGroup
    public val createChannel: CreateChannelUseCase get() = entryPoints.createChannel
    public val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = entryPoints.addMemberToConversationUseCase
    public val addServiceToConversationUseCase: AddServiceToConversationUseCase
        get() = entryPoints.addServiceToConversationUseCase
    public val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = entryPoints.getOrCreateOneToOneConversationUseCase
    public val isOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
        get() = entryPoints.isOneToOneConversationCreatedUseCase
    public val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = entryPoints.updateConversationMutedStatus
    public val updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
        get() = entryPoints.updateConversationArchivedStatus

    @Deprecated(
        "Name is misleading, and this field will be removed",
        ReplaceWith("observePendingConnectionRequests")
    )
    internal val observeConnectionList: ObserveConnectionListUseCase get() = observePendingConnectionRequests

    internal val observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase
        get() = entryPoints.observePendingConnectionRequests
    public val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = entryPoints.markConnectionRequestAsNotified
    public val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
        get() = entryPoints.updateConversationReadDateUseCase
    public val markConversationAsReadLocally: MarkConversationAsReadLocallyUseCase
        get() = entryPoints.markConversationAsReadLocally
    public val updateConversationAccess: UpdateConversationAccessRoleUseCase get() = entryPoints.updateConversationAccess
    public val changeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase
        get() = entryPoints.changeAccessForAppsInConversation
    public val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
        get() = entryPoints.updateConversationMemberRole
    public val removeMemberFromConversation: RemoveMemberFromConversationUseCase
        get() = entryPoints.removeMemberFromConversation
    public val leaveConversation: LeaveConversationUseCase get() = entryPoints.leaveConversation
    public val promoteAdminAndLeaveConversation: PromoteAdminAndLeaveConversationUseCase
        get() = entryPoints.promoteAdminAndLeaveConversation
    public val checkConversationLeaveConditions: CheckConversationLeaveConditionsUseCase
        get() = entryPoints.checkConversationLeaveConditions
    public val renameConversation: RenameConversationUseCase get() = entryPoints.renameConversation
    internal val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
        get() = entryPoints.updateMLSGroupsKeyingMaterials
    internal val clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase
        get() = entryPoints.clearConversationAssetsLocally
    public val clearConversationContent: ClearConversationContentUseCase get() = entryPoints.clearConversationContent
    public val markConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase
        get() = entryPoints.markConversationAsDeletedLocallyUseCase
    public val deleteConversationLocallyUseCase: DeleteConversationLocallyUseCase
        get() = entryPoints.deleteConversationLocallyUseCase
    public val joinConversationViaCode: JoinConversationViaCodeUseCase get() = entryPoints.joinConversationViaCode
    public val checkIConversationInviteCode: CheckConversationInviteCodeUseCase
        get() = entryPoints.checkIConversationInviteCode
    public val updateConversationReceiptMode: UpdateConversationReceiptModeUseCase
        get() = entryPoints.updateConversationReceiptMode
    public val generateGuestRoomLink: GenerateGuestRoomLinkUseCase get() = entryPoints.generateGuestRoomLink
    public val revokeGuestRoomLink: RevokeGuestRoomLinkUseCase get() = entryPoints.revokeGuestRoomLink
    public val observeGuestRoomLink: ObserveGuestRoomLinkUseCase get() = entryPoints.observeGuestRoomLink
    public val updateMessageTimer: UpdateMessageTimerUseCase get() = entryPoints.updateMessageTimer
    public val getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
        get() = entryPoints.getConversationUnreadEventsCountUseCase
    public val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
        get() = entryPoints.refreshConversationsWithoutMetadata
    public val canCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
        get() = entryPoints.canCreatePasswordProtectedLinks
    public val observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
        get() = entryPoints.observeArchivedUnreadConversationsCount
    internal val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl
        get() = entryPoints.typingIndicatorIncomingRepository
    internal val typingIndicatorOutgoingRepository: TypingIndicatorOutgoingRepositoryImpl
        get() = entryPoints.typingIndicatorOutgoingRepository
    public val sendTypingEvent: SendTypingEventUseCase get() = entryPoints.sendTypingEvent
    public val observeUsersTyping: ObserveUsersTypingUseCase get() = entryPoints.observeUsersTyping
    public val clearUsersTypingEvents: ClearUsersTypingEventsUseCase get() = entryPoints.clearUsersTypingEvents
    public val setUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
        get() = entryPoints.setUserInformedAboutVerificationBeforeMessagingUseCase
    public val observeInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
        get() = entryPoints.observeInformAboutVerificationBeforeMessagingFlagUseCase
    public val setNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
        get() = entryPoints.setNotifiedAboutConversationUnderLegalHold
    public val observeConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
        get() = entryPoints.observeConversationUnderLegalHoldNotified
    public val syncConversationCode: SyncConversationCodeUseCase get() = entryPoints.syncConversationCode
    public val observeConversationsFromFolder: ObserveConversationsFromFolderUseCase
        get() = entryPoints.observeConversationsFromFolder
    public val getFavoriteFolder: GetFavoriteFolderUseCase get() = entryPoints.getFavoriteFolder
    public val addConversationToFavorites: AddConversationToFavoritesUseCase
        get() = entryPoints.addConversationToFavorites
    public val removeConversationFromFavorites: RemoveConversationFromFavoritesUseCase
        get() = entryPoints.removeConversationFromFavorites
    public val observeUserFolders: ObserveUserFoldersUseCase get() = entryPoints.observeUserFolders
    public val moveConversationToFolder: MoveConversationToFolderUseCase get() = entryPoints.moveConversationToFolder
    public val removeConversationFromFolder: RemoveConversationFromFolderUseCase
        get() = entryPoints.removeConversationFromFolder
    public val createConversationFolder: CreateConversationFolderUseCase get() = entryPoints.createConversationFolder
    public val isSelfUserViewerOnConversation: IsSelfUserViewerOnConversationUseCase
        get() = entryPoints.isSelfUserViewerOnConversation
}
