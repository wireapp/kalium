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
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
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
import com.wire.kalium.logic.feature.conversation.folder.ManageConversationFolderUseCase
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

/** Compatibility facade over the individual bindings retained by the user owner. */
public class ConversationScope internal constructor(private val api: LegacyConversationApi) {
    internal val conversationRepository: ConversationRepository
        get() = api.conversationConversationRepository

    internal val callRepository: CallRepository
        get() = api.conversationCallRepository

    internal val selfUserId: UserId
        get() = api.conversationSelfUserId

    internal val selfTeamIdProvider: SelfTeamIdProvider
        get() = api.conversationSelfTeamIdProvider

    internal val messageRepository: MessageRepository
        get() = api.conversationMessageRepository

    internal val assetRepository: AssetRepository
        get() = api.conversationAssetRepository

    internal val dispatcher: KaliumDispatcher
        get() = api.conversationDispatcher

    public val getConversations: GetConversationsUseCase
        get() = api.conversationGetConversations

    internal val getConversationDetails: GetConversationUseCase
        get() = api.conversationGetConversationDetails

    public val getOneToOneConversation: GetOneToOneConversationDetailsUseCase
        get() = api.conversationGetOneToOneConversation

    public val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = api.conversationObserveConversationListDetails

    public val observeConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase
        get() = api.conversationObserveConversationListDetailsWithEvents

    public val observeConversationMembers: ObserveConversationMembersUseCase
        get() = api.conversationObserveConversationMembers

    public val getMembersToMention: MembersToMentionUseCase
        get() = api.conversationGetMembersToMention

    public val observeEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase
        get() = api.conversationObserveEligibleMembersForConversationAdminRole

    public val observeUserListById: ObserveUserListByIdUseCase
        get() = api.conversationObserveUserListById

    public val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = api.conversationObserveConversationDetails

    public val observeConversationRoleForUser: ObserveConversationRoleForUserUseCase
        get() = api.conversationObserveConversationRoleForUser

    public val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
        get() = api.conversationGetConversationProtocolInfo

    public val notifyConversationIsOpen: NotifyConversationIsOpenUseCase
        get() = api.conversationNotifyConversationIsOpen

    public val observeIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
        get() = api.conversationObserveIsSelfUserMemberUseCase

    public val observeConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
        get() = api.conversationObserveConversationInteractionAvailabilityUseCase

    public val deleteTeamConversation: DeleteTeamConversationUseCase
        get() = api.conversationDeleteTeamConversation

    internal val createGroupConversation: GroupConversationCreator
        get() = api.conversationCreateGroupConversation

    public val createRegularGroup: CreateRegularGroupUseCase
        get() = api.conversationCreateRegularGroup

    public val createChannel: CreateChannelUseCase
        get() = api.conversationCreateChannel

    public val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = api.conversationAddMemberToConversationUseCase

    public val addServiceToConversationUseCase: AddServiceToConversationUseCase
        get() = api.conversationAddServiceToConversationUseCase

    public val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = api.conversationGetOrCreateOneToOneConversationUseCase

    public val checkOneToOneConversationIsReadyUseCase: CheckOneToOneConversationIsReadyUseCase
        get() = api.conversationCheckOneToOneConversationIsReadyUseCase

    public val isOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
        get() = api.conversationIsOneToOneConversationCreatedUseCase

    public val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = api.conversationUpdateConversationMutedStatus

    public val updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
        get() = api.conversationUpdateConversationArchivedStatus

    @Deprecated("Name is misleading, and this field will be removed", ReplaceWith("observePendingConnectionRequests"))
    internal val observeConnectionList: ObserveConnectionListUseCase
        get() = api.conversationObserveConnectionList

    public val observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase
        get() = api.conversationObservePendingConnectionRequests

    public val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = api.conversationMarkConnectionRequestAsNotified

    public val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
        get() = api.conversationUpdateConversationReadDateUseCase

    public val markConversationAsReadLocally: MarkConversationAsReadLocallyUseCase
        get() = api.conversationMarkConversationAsReadLocally

    public val updateConversationAccess: UpdateConversationAccessRoleUseCase
        get() = api.conversationUpdateConversationAccess

    public val changeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase
        get() = api.conversationChangeAccessForAppsInConversation

    public val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
        get() = api.conversationUpdateConversationMemberRole

    public val removeMemberFromConversation: RemoveMemberFromConversationUseCase
        get() = api.conversationRemoveMemberFromConversation

    public val leaveConversation: LeaveConversationUseCase
        get() = api.conversationLeaveConversation

    public val promoteAdminAndLeaveConversation: PromoteAdminAndLeaveConversationUseCase
        get() = api.conversationPromoteAdminAndLeaveConversation

    public val checkConversationLeaveConditions: CheckConversationLeaveConditionsUseCase
        get() = api.conversationCheckConversationLeaveConditions

    public val renameConversation: RenameConversationUseCase
        get() = api.conversationRenameConversation

    internal val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
        get() = api.conversationUpdateMLSGroupsKeyingMaterials

    internal val clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase
        get() = api.conversationClearConversationAssetsLocally

    public val clearConversationContent: ClearConversationContentUseCase
        get() = api.conversationClearConversationContent

    public val markConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase
        get() = api.conversationMarkConversationAsDeletedLocallyUseCase

    public val deleteConversationLocallyUseCase: DeleteConversationLocallyUseCase
        get() = api.conversationDeleteConversationLocallyUseCase

    public val joinConversationViaCode: JoinConversationViaCodeUseCase
        get() = api.conversationJoinConversationViaCode

    public val checkIConversationInviteCode: CheckConversationInviteCodeUseCase
        get() = api.conversationCheckIConversationInviteCode

    public val updateConversationReceiptMode: UpdateConversationReceiptModeUseCase
        get() = api.conversationUpdateConversationReceiptMode

    public val generateGuestRoomLink: GenerateGuestRoomLinkUseCase
        get() = api.conversationGenerateGuestRoomLink

    public val revokeGuestRoomLink: RevokeGuestRoomLinkUseCase
        get() = api.conversationRevokeGuestRoomLink

    public val observeGuestRoomLink: ObserveGuestRoomLinkUseCase
        get() = api.conversationObserveGuestRoomLink

    public val updateMessageTimer: UpdateMessageTimerUseCase
        get() = api.conversationUpdateMessageTimer

    public val getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
        get() = api.conversationGetConversationUnreadEventsCountUseCase

    public val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
        get() = api.conversationRefreshConversationsWithoutMetadata

    public val canCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
        get() = api.conversationCanCreatePasswordProtectedLinks

    public val observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
        get() = api.conversationObserveArchivedUnreadConversationsCount

    internal val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl
        get() = api.conversationTypingIndicatorIncomingRepository

    internal val typingIndicatorOutgoingRepository: TypingIndicatorOutgoingRepositoryImpl
        get() = api.conversationTypingIndicatorOutgoingRepository

    public val sendTypingEvent: SendTypingEventUseCase
        get() = api.conversationSendTypingEvent

    public val observeUsersTyping: ObserveUsersTypingUseCase
        get() = api.conversationObserveUsersTyping

    public val clearUsersTypingEvents: ClearUsersTypingEventsUseCase
        get() = api.conversationClearUsersTypingEvents

    public val setUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
        get() = api.conversationSetUserInformedAboutVerificationBeforeMessagingUseCase

    public val observeInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
        get() = api.conversationObserveInformAboutVerificationBeforeMessagingFlagUseCase

    public val setNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
        get() = api.conversationSetNotifiedAboutConversationUnderLegalHold

    public val observeConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
        get() = api.conversationObserveConversationUnderLegalHoldNotified

    public val syncConversationCode: SyncConversationCodeUseCase
        get() = api.conversationSyncConversationCode

    public val observeConversationsFromFolder: ObserveConversationsFromFolderUseCase
        get() = api.conversationObserveConversationsFromFolder

    public val getFavoriteFolder: GetFavoriteFolderUseCase
        get() = api.conversationGetFavoriteFolder

    public val addConversationToFavorites: AddConversationToFavoritesUseCase
        get() = api.conversationAddConversationToFavorites

    public val removeConversationFromFavorites: RemoveConversationFromFavoritesUseCase
        get() = api.conversationRemoveConversationFromFavorites

    public val observeUserFolders: ObserveUserFoldersUseCase
        get() = api.conversationObserveUserFolders

    public val moveConversationToFolder: MoveConversationToFolderUseCase
        get() = api.conversationMoveConversationToFolder

    public val removeConversationFromFolder: RemoveConversationFromFolderUseCase
        get() = api.conversationRemoveConversationFromFolder

    public val createConversationFolder: CreateConversationFolderUseCase
        get() = api.conversationCreateConversationFolder

    public val manageConversationFolder: ManageConversationFolderUseCase
        get() = api.conversationManageConversationFolder

    public val isSelfUserViewerOnConversation: IsSelfUserViewerOnConversationUseCase
        get() = api.conversationIsSelfUserViewerOnConversation

    internal val bindingGetPaginatedFlowOfConversationDetailsWithEventsBySearchQuery:
        GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase
        get() = api.conversationGetPaginatedFlowOfConversationDetailsWithEventsBySearchQuery

    internal val bindingGetPaginatedFlowOfConversationDetailsWithMembersBySearchQuery:
        GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase
        get() = api.conversationGetPaginatedFlowOfConversationDetailsWithMembersBySearchQuery

    internal val bindingObserveConversationListItems: ObserveConversationListItemsUseCase
        get() = api.conversationObserveConversationListItems

}
