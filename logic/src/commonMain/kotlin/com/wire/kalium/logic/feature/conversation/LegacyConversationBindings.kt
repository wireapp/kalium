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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorSenderHandler
import com.wire.kalium.logic.data.conversation.TypingIndicatorSenderHandlerImpl
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.feature.connection.ObservePendingConnectionRequestsUseCase
import com.wire.kalium.logic.feature.connection.ObservePendingConnectionRequestsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.apps.ChangeAccessForAppsInConversationUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateChannelUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCaseImpl
import com.wire.kalium.logic.feature.conversation.createconversation.GroupConversationCreator
import com.wire.kalium.logic.feature.conversation.createconversation.GroupConversationCreatorImpl
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationLocallyUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationLocallyUseCaseImpl
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.delete.MarkConversationAsDeletedLocallyUseCase
import com.wire.kalium.logic.feature.conversation.delete.MarkConversationAsDeletedLocallyUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.AddConversationToFavoritesUseCase
import com.wire.kalium.logic.feature.conversation.folder.AddConversationToFavoritesUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.CreateConversationFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.CreateConversationFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.MoveConversationToFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.MoveConversationToFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.ManageConversationFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.ManageConversationFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.ObserveConversationsFromFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.ObserveConversationsFromFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.ObserveUserFoldersUseCase
import com.wire.kalium.logic.feature.conversation.folder.ObserveUserFoldersUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFavoritesUseCase
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFavoritesUseCaseImpl
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFolderUseCase
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFolderUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.CanCreatePasswordProtectedLinksUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.GenerateGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.GenerateGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.RevokeGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.RevokeGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCase
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.ParallelConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveSelfUserUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import com.wire.kalium.logic.di.UserLifetime
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

@Qualifier
internal annotation class LegacyConversationBinding(val value: String)

/** Deferred resource inputs supplied by the existing user owner. */
internal interface LegacyConversationDependencies {
    val conversationRepository: ConversationRepository
    val callRepository: CallRepository
    val conversationGroupRepository: ConversationGroupRepository
    val connectionRepository: ConnectionRepository
    val userRepository: UserRepository
    val conversationFolderRepository: ConversationFolderRepository
    val syncManager: SyncManager
    val mlsConversationRepository: MLSConversationRepository
    val currentClientIdProvider: CurrentClientIdProvider
    val messageSender: MessageSender
    val teamRepository: TeamRepository
    val slowSyncRepository: SlowSyncRepository
    val selfUserId: UserId
    val selfConversationIdProvider: SelfConversationIdProvider
    val persistMessage: PersistMessageUseCase
    val selfTeamIdProvider: SelfTeamIdProvider
    val sendConfirmation: SendConfirmationUseCase
    val renamedConversationHandler: RenamedConversationEventHandler
    val serverConfigRepository: ServerConfigRepository
    val userStorage: UserStorage
    val userPropertyRepository: UserPropertyRepository
    val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
    val oneOnOneResolver: OneOnOneResolver
    val scope: CoroutineScope
    val kaliumLogger: KaliumLogger
    val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
    val serverConfigLinks: ServerConfig.Links
    val messageRepository: MessageRepository
    val assetRepository: AssetRepository
    val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
    val deleteConversationUseCase: DeleteConversationUseCase
    val persistConversationsUseCase: PersistConversationsUseCase
    val transactionProvider: CryptoTransactionProvider
    val resetMLSConversationUseCase: ResetMLSConversationUseCase
    val systemMessageInserter: SystemMessageInserter
    val persistenceEventHookNotifier: PersistenceEventHookNotifier
    val memberJoinEventHandler: MemberJoinEventHandler
    val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
    val pendingActionsRepository: PendingActionsRepository
    val dispatcher: KaliumDispatcher
}

/** Individual legacy accessors; qualification preserves each original capture. */
internal interface LegacyConversationApi {
    @get:LegacyConversationBinding("conversationRepository")
    val conversationConversationRepository: ConversationRepository

    @get:LegacyConversationBinding("callRepository")
    val conversationCallRepository: CallRepository

    @get:LegacyConversationBinding("conversationGroupRepository")
    val conversationConversationGroupRepository: ConversationGroupRepository

    @get:LegacyConversationBinding("connectionRepository")
    val conversationConnectionRepository: ConnectionRepository

    @get:LegacyConversationBinding("userRepository")
    val conversationUserRepository: UserRepository

    @get:LegacyConversationBinding("conversationFolderRepository")
    val conversationConversationFolderRepository: ConversationFolderRepository

    @get:LegacyConversationBinding("syncManager")
    val conversationSyncManager: SyncManager

    @get:LegacyConversationBinding("mlsConversationRepository")
    val conversationMlsConversationRepository: MLSConversationRepository

    @get:LegacyConversationBinding("currentClientIdProvider")
    val conversationCurrentClientIdProvider: CurrentClientIdProvider

    @get:LegacyConversationBinding("messageSender")
    val conversationMessageSender: MessageSender

    @get:LegacyConversationBinding("teamRepository")
    val conversationTeamRepository: TeamRepository

    @get:LegacyConversationBinding("slowSyncRepository")
    val conversationSlowSyncRepository: SlowSyncRepository

    @get:LegacyConversationBinding("selfUserId")
    val conversationSelfUserId: UserId

    @get:LegacyConversationBinding("selfConversationIdProvider")
    val conversationSelfConversationIdProvider: SelfConversationIdProvider

    @get:LegacyConversationBinding("persistMessage")
    val conversationPersistMessage: PersistMessageUseCase

    @get:LegacyConversationBinding("selfTeamIdProvider")
    val conversationSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyConversationBinding("sendConfirmation")
    val conversationSendConfirmation: SendConfirmationUseCase

    @get:LegacyConversationBinding("renamedConversationHandler")
    val conversationRenamedConversationHandler: RenamedConversationEventHandler

    @get:LegacyConversationBinding("serverConfigRepository")
    val conversationServerConfigRepository: ServerConfigRepository

    @get:LegacyConversationBinding("userStorage")
    val conversationUserStorage: UserStorage

    @get:LegacyConversationBinding("userPropertyRepository")
    val conversationUserPropertyRepository: UserPropertyRepository

    @get:LegacyConversationBinding("deleteEphemeralMessageEndDate")
    val conversationDeleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase

    @get:LegacyConversationBinding("oneOnOneResolver")
    val conversationOneOnOneResolver: OneOnOneResolver

    @get:LegacyConversationBinding("scope")
    val conversationScope: CoroutineScope

    @get:LegacyConversationBinding("kaliumLogger")
    val conversationKaliumLogger: KaliumLogger

    @get:LegacyConversationBinding("refreshUsersWithoutMetadata")
    val conversationRefreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase

    @get:LegacyConversationBinding("serverConfigLinks")
    val conversationServerConfigLinks: ServerConfig.Links

    @get:LegacyConversationBinding("messageRepository")
    val conversationMessageRepository: MessageRepository

    @get:LegacyConversationBinding("assetRepository")
    val conversationAssetRepository: AssetRepository

    @get:LegacyConversationBinding("newGroupConversationSystemMessagesCreator")
    val conversationNewGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator

    @get:LegacyConversationBinding("deleteConversationUseCase")
    val conversationDeleteConversationUseCase: DeleteConversationUseCase

    @get:LegacyConversationBinding("persistConversationsUseCase")
    val conversationPersistConversationsUseCase: PersistConversationsUseCase

    @get:LegacyConversationBinding("transactionProvider")
    val conversationTransactionProvider: CryptoTransactionProvider

    @get:LegacyConversationBinding("resetMLSConversationUseCase")
    val conversationResetMLSConversationUseCase: ResetMLSConversationUseCase

    @get:LegacyConversationBinding("systemMessageInserter")
    val conversationSystemMessageInserter: SystemMessageInserter

    @get:LegacyConversationBinding("persistenceEventHookNotifier")
    val conversationPersistenceEventHookNotifier: PersistenceEventHookNotifier

    @get:LegacyConversationBinding("memberJoinEventHandler")
    val conversationMemberJoinEventHandler: MemberJoinEventHandler

    @get:LegacyConversationBinding("joinExistingMLSConversation")
    val conversationJoinExistingMLSConversation: JoinExistingMLSConversationUseCase

    @get:LegacyConversationBinding("pendingActionsRepository")
    val conversationPendingActionsRepository: PendingActionsRepository

    @get:LegacyConversationBinding("dispatcher")
    val conversationDispatcher: KaliumDispatcher

    @get:LegacyConversationBinding("getConversations")
    val conversationGetConversations: GetConversationsUseCase

    @get:LegacyConversationBinding("getConversationDetails")
    val conversationGetConversationDetails: GetConversationUseCase

    @get:LegacyConversationBinding("getOneToOneConversation")
    val conversationGetOneToOneConversation: GetOneToOneConversationDetailsUseCase

    @get:LegacyConversationBinding("observeConversationListDetails")
    val conversationObserveConversationListDetails: ObserveConversationListDetailsUseCase

    @get:LegacyConversationBinding("observeConversationListDetailsWithEvents")
    val conversationObserveConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase

    @get:LegacyConversationBinding("observeConversationMembers")
    val conversationObserveConversationMembers: ObserveConversationMembersUseCase

    @get:LegacyConversationBinding("getMembersToMention")
    val conversationGetMembersToMention: MembersToMentionUseCase

    @get:LegacyConversationBinding("observeEligibleMembersForConversationAdminRole")
    val conversationObserveEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase

    @get:LegacyConversationBinding("observeUserListById")
    val conversationObserveUserListById: ObserveUserListByIdUseCase

    @get:LegacyConversationBinding("observeConversationDetails")
    val conversationObserveConversationDetails: ObserveConversationDetailsUseCase

    @get:LegacyConversationBinding("observeConversationRoleForUser")
    val conversationObserveConversationRoleForUser: ObserveConversationRoleForUserUseCase

    @get:LegacyConversationBinding("getConversationProtocolInfo")
    val conversationGetConversationProtocolInfo: GetConversationProtocolInfoUseCase

    @get:LegacyConversationBinding("notifyConversationIsOpen")
    val conversationNotifyConversationIsOpen: NotifyConversationIsOpenUseCase

    @get:LegacyConversationBinding("observeIsSelfUserMemberUseCase")
    val conversationObserveIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase

    @get:LegacyConversationBinding("observeConversationInteractionAvailabilityUseCase")
    val conversationObserveConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase

    @get:LegacyConversationBinding("deleteTeamConversation")
    val conversationDeleteTeamConversation: DeleteTeamConversationUseCase

    @get:LegacyConversationBinding("createGroupConversation")
    val conversationCreateGroupConversation: GroupConversationCreator

    @get:LegacyConversationBinding("createRegularGroup")
    val conversationCreateRegularGroup: CreateRegularGroupUseCase

    @get:LegacyConversationBinding("createChannel")
    val conversationCreateChannel: CreateChannelUseCase

    @get:LegacyConversationBinding("addMemberToConversationUseCase")
    val conversationAddMemberToConversationUseCase: AddMemberToConversationUseCase

    @get:LegacyConversationBinding("addServiceToConversationUseCase")
    val conversationAddServiceToConversationUseCase: AddServiceToConversationUseCase

    @get:LegacyConversationBinding("getOrCreateOneToOneConversationUseCase")
    val conversationGetOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase

    @get:LegacyConversationBinding("checkOneToOneConversationIsReadyUseCase")
    val conversationCheckOneToOneConversationIsReadyUseCase: CheckOneToOneConversationIsReadyUseCase

    @get:LegacyConversationBinding("isOneToOneConversationCreatedUseCase")
    val conversationIsOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase

    @get:LegacyConversationBinding("updateConversationMutedStatus")
    val conversationUpdateConversationMutedStatus: UpdateConversationMutedStatusUseCase

    @get:LegacyConversationBinding("updateConversationArchivedStatus")
    val conversationUpdateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase

    @get:LegacyConversationBinding("observeConnectionList")
    val conversationObserveConnectionList: ObserveConnectionListUseCase

    @get:LegacyConversationBinding("observePendingConnectionRequests")
    val conversationObservePendingConnectionRequests: ObservePendingConnectionRequestsUseCase

    @get:LegacyConversationBinding("markConnectionRequestAsNotified")
    val conversationMarkConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase

    @get:LegacyConversationBinding("conversationWorkQueue")
    val conversationConversationWorkQueue: ConversationWorkQueue

    @get:LegacyConversationBinding("updateConversationReadDateUseCase")
    val conversationUpdateConversationReadDateUseCase: UpdateConversationReadDateUseCase

    @get:LegacyConversationBinding("markConversationAsReadLocally")
    val conversationMarkConversationAsReadLocally: MarkConversationAsReadLocallyUseCase

    @get:LegacyConversationBinding("updateConversationAccess")
    val conversationUpdateConversationAccess: UpdateConversationAccessRoleUseCase

    @get:LegacyConversationBinding("changeAccessForAppsInConversation")
    val conversationChangeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase

    @get:LegacyConversationBinding("updateConversationMemberRole")
    val conversationUpdateConversationMemberRole: UpdateConversationMemberRoleUseCase

    @get:LegacyConversationBinding("removeMemberFromConversation")
    val conversationRemoveMemberFromConversation: RemoveMemberFromConversationUseCase

    @get:LegacyConversationBinding("leaveConversation")
    val conversationLeaveConversation: LeaveConversationUseCase

    @get:LegacyConversationBinding("promoteAdminAndLeaveConversation")
    val conversationPromoteAdminAndLeaveConversation: PromoteAdminAndLeaveConversationUseCase

    @get:LegacyConversationBinding("checkConversationLeaveConditions")
    val conversationCheckConversationLeaveConditions: CheckConversationLeaveConditionsUseCase

    @get:LegacyConversationBinding("renameConversation")
    val conversationRenameConversation: RenameConversationUseCase

    @get:LegacyConversationBinding("updateMLSGroupsKeyingMaterials")
    val conversationUpdateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase

    @get:LegacyConversationBinding("clearConversationAssetsLocally")
    val conversationClearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase

    @get:LegacyConversationBinding("clearConversationContent")
    val conversationClearConversationContent: ClearConversationContentUseCase

    @get:LegacyConversationBinding("markConversationAsDeletedLocallyUseCase")
    val conversationMarkConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase

    @get:LegacyConversationBinding("deleteConversationLocallyUseCase")
    val conversationDeleteConversationLocallyUseCase: DeleteConversationLocallyUseCase

    @get:LegacyConversationBinding("joinConversationViaCode")
    val conversationJoinConversationViaCode: JoinConversationViaCodeUseCase

    @get:LegacyConversationBinding("checkIConversationInviteCode")
    val conversationCheckIConversationInviteCode: CheckConversationInviteCodeUseCase

    @get:LegacyConversationBinding("updateConversationReceiptMode")
    val conversationUpdateConversationReceiptMode: UpdateConversationReceiptModeUseCase

    @get:LegacyConversationBinding("generateGuestRoomLink")
    val conversationGenerateGuestRoomLink: GenerateGuestRoomLinkUseCase

    @get:LegacyConversationBinding("revokeGuestRoomLink")
    val conversationRevokeGuestRoomLink: RevokeGuestRoomLinkUseCase

    @get:LegacyConversationBinding("observeGuestRoomLink")
    val conversationObserveGuestRoomLink: ObserveGuestRoomLinkUseCase

    @get:LegacyConversationBinding("updateMessageTimer")
    val conversationUpdateMessageTimer: UpdateMessageTimerUseCase

    @get:LegacyConversationBinding("getConversationUnreadEventsCountUseCase")
    val conversationGetConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase

    @get:LegacyConversationBinding("refreshConversationsWithoutMetadata")
    val conversationRefreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase

    @get:LegacyConversationBinding("canCreatePasswordProtectedLinks")
    val conversationCanCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase

    @get:LegacyConversationBinding("observeArchivedUnreadConversationsCount")
    val conversationObserveArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase

    @get:LegacyConversationBinding("typingIndicatorSenderHandler")
    val conversationTypingIndicatorSenderHandler: TypingIndicatorSenderHandler

    @get:LegacyConversationBinding("typingIndicatorIncomingRepository")
    val conversationTypingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl

    @get:LegacyConversationBinding("typingIndicatorOutgoingRepository")
    val conversationTypingIndicatorOutgoingRepository: TypingIndicatorOutgoingRepositoryImpl

    @get:LegacyConversationBinding("sendTypingEvent")
    val conversationSendTypingEvent: SendTypingEventUseCase

    @get:LegacyConversationBinding("observeUsersTyping")
    val conversationObserveUsersTyping: ObserveUsersTypingUseCase

    @get:LegacyConversationBinding("clearUsersTypingEvents")
    val conversationClearUsersTypingEvents: ClearUsersTypingEventsUseCase

    @get:LegacyConversationBinding("setUserInformedAboutVerificationBeforeMessagingUseCase")
    val conversationSetUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase

    @get:LegacyConversationBinding("observeInformAboutVerificationBeforeMessagingFlagUseCase")
    val conversationObserveInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase

    @get:LegacyConversationBinding("setNotifiedAboutConversationUnderLegalHold")
    val conversationSetNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase

    @get:LegacyConversationBinding("observeConversationUnderLegalHoldNotified")
    val conversationObserveConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase

    @get:LegacyConversationBinding("syncConversationCode")
    val conversationSyncConversationCode: SyncConversationCodeUseCase

    @get:LegacyConversationBinding("observeConversationsFromFolder")
    val conversationObserveConversationsFromFolder: ObserveConversationsFromFolderUseCase

    @get:LegacyConversationBinding("getFavoriteFolder")
    val conversationGetFavoriteFolder: GetFavoriteFolderUseCase

    @get:LegacyConversationBinding("addConversationToFavorites")
    val conversationAddConversationToFavorites: AddConversationToFavoritesUseCase

    @get:LegacyConversationBinding("removeConversationFromFavorites")
    val conversationRemoveConversationFromFavorites: RemoveConversationFromFavoritesUseCase

    @get:LegacyConversationBinding("observeUserFolders")
    val conversationObserveUserFolders: ObserveUserFoldersUseCase

    @get:LegacyConversationBinding("moveConversationToFolder")
    val conversationMoveConversationToFolder: MoveConversationToFolderUseCase

    @get:LegacyConversationBinding("removeConversationFromFolder")
    val conversationRemoveConversationFromFolder: RemoveConversationFromFolderUseCase

    @get:LegacyConversationBinding("createConversationFolder")
    val conversationCreateConversationFolder: CreateConversationFolderUseCase

    @get:LegacyConversationBinding("manageConversationFolder")
    val conversationManageConversationFolder: ManageConversationFolderUseCase

    @get:LegacyConversationBinding("isSelfUserViewerOnConversation")
    val conversationIsSelfUserViewerOnConversation: IsSelfUserViewerOnConversationUseCase

}

/** Bindings share the user graph; feature packages do not create runtime graphs. */
internal interface LegacyConversationBindings : LegacyConversationApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("conversationRepository")
    fun provideConversationConversationRepository(
        dependencies: LegacyConversationDependencies,
    ): ConversationRepository = dependencies.conversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("callRepository")
    fun provideConversationCallRepository(
        dependencies: LegacyConversationDependencies,
    ): CallRepository = dependencies.callRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("conversationGroupRepository")
    fun provideConversationConversationGroupRepository(
        dependencies: LegacyConversationDependencies,
    ): ConversationGroupRepository = dependencies.conversationGroupRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("connectionRepository")
    fun provideConversationConnectionRepository(
        dependencies: LegacyConversationDependencies,
    ): ConnectionRepository = dependencies.connectionRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("userRepository")
    fun provideConversationUserRepository(
        dependencies: LegacyConversationDependencies,
    ): UserRepository = dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("conversationFolderRepository")
    fun provideConversationConversationFolderRepository(
        dependencies: LegacyConversationDependencies,
    ): ConversationFolderRepository = dependencies.conversationFolderRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("syncManager")
    fun provideConversationSyncManager(
        dependencies: LegacyConversationDependencies,
    ): SyncManager = dependencies.syncManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("mlsConversationRepository")
    fun provideConversationMlsConversationRepository(
        dependencies: LegacyConversationDependencies,
    ): MLSConversationRepository = dependencies.mlsConversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("currentClientIdProvider")
    fun provideConversationCurrentClientIdProvider(
        dependencies: LegacyConversationDependencies,
    ): CurrentClientIdProvider = dependencies.currentClientIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("messageSender")
    fun provideConversationMessageSender(
        dependencies: LegacyConversationDependencies,
    ): MessageSender = dependencies.messageSender

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("teamRepository")
    fun provideConversationTeamRepository(
        dependencies: LegacyConversationDependencies,
    ): TeamRepository = dependencies.teamRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("slowSyncRepository")
    fun provideConversationSlowSyncRepository(
        dependencies: LegacyConversationDependencies,
    ): SlowSyncRepository = dependencies.slowSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("selfUserId")
    fun provideConversationSelfUserId(
        dependencies: LegacyConversationDependencies,
    ): UserId = dependencies.selfUserId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("selfConversationIdProvider")
    fun provideConversationSelfConversationIdProvider(
        dependencies: LegacyConversationDependencies,
    ): SelfConversationIdProvider = dependencies.selfConversationIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("persistMessage")
    fun provideConversationPersistMessage(
        dependencies: LegacyConversationDependencies,
    ): PersistMessageUseCase = dependencies.persistMessage

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("selfTeamIdProvider")
    fun provideConversationSelfTeamIdProvider(
        dependencies: LegacyConversationDependencies,
    ): SelfTeamIdProvider = dependencies.selfTeamIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("sendConfirmation")
    fun provideConversationSendConfirmation(
        dependencies: LegacyConversationDependencies,
    ): SendConfirmationUseCase = dependencies.sendConfirmation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("renamedConversationHandler")
    fun provideConversationRenamedConversationHandler(
        dependencies: LegacyConversationDependencies,
    ): RenamedConversationEventHandler = dependencies.renamedConversationHandler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("serverConfigRepository")
    fun provideConversationServerConfigRepository(
        dependencies: LegacyConversationDependencies,
    ): ServerConfigRepository = dependencies.serverConfigRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("userStorage")
    fun provideConversationUserStorage(
        dependencies: LegacyConversationDependencies,
    ): UserStorage = dependencies.userStorage

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("userPropertyRepository")
    fun provideConversationUserPropertyRepository(
        dependencies: LegacyConversationDependencies,
    ): UserPropertyRepository = dependencies.userPropertyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("deleteEphemeralMessageEndDate")
    fun provideConversationDeleteEphemeralMessageEndDate(
        dependencies: LegacyConversationDependencies,
    ): DeleteEphemeralMessagesAfterEndDateUseCase = dependencies.deleteEphemeralMessageEndDate

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("oneOnOneResolver")
    fun provideConversationOneOnOneResolver(
        dependencies: LegacyConversationDependencies,
    ): OneOnOneResolver = dependencies.oneOnOneResolver

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("scope")
    fun provideConversationScope(
        dependencies: LegacyConversationDependencies,
    ): CoroutineScope = dependencies.scope

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("kaliumLogger")
    fun provideConversationKaliumLogger(
        dependencies: LegacyConversationDependencies,
    ): KaliumLogger = dependencies.kaliumLogger

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("refreshUsersWithoutMetadata")
    fun provideConversationRefreshUsersWithoutMetadata(
        dependencies: LegacyConversationDependencies,
    ): RefreshUsersWithoutMetadataUseCase = dependencies.refreshUsersWithoutMetadata

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("serverConfigLinks")
    fun provideConversationServerConfigLinks(
        dependencies: LegacyConversationDependencies,
    ): ServerConfig.Links = dependencies.serverConfigLinks

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("messageRepository")
    fun provideConversationMessageRepository(
        dependencies: LegacyConversationDependencies,
    ): MessageRepository = dependencies.messageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("assetRepository")
    fun provideConversationAssetRepository(
        dependencies: LegacyConversationDependencies,
    ): AssetRepository = dependencies.assetRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("newGroupConversationSystemMessagesCreator")
    fun provideConversationNewGroupConversationSystemMessagesCreator(
        dependencies: LegacyConversationDependencies,
    ): NewGroupConversationSystemMessagesCreator = dependencies.newGroupConversationSystemMessagesCreator

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("deleteConversationUseCase")
    fun provideConversationDeleteConversationUseCase(
        dependencies: LegacyConversationDependencies,
    ): DeleteConversationUseCase = dependencies.deleteConversationUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("persistConversationsUseCase")
    fun provideConversationPersistConversationsUseCase(
        dependencies: LegacyConversationDependencies,
    ): PersistConversationsUseCase = dependencies.persistConversationsUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("transactionProvider")
    fun provideConversationTransactionProvider(
        dependencies: LegacyConversationDependencies,
    ): CryptoTransactionProvider = dependencies.transactionProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("resetMLSConversationUseCase")
    fun provideConversationResetMLSConversationUseCase(
        dependencies: LegacyConversationDependencies,
    ): ResetMLSConversationUseCase = dependencies.resetMLSConversationUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("systemMessageInserter")
    fun provideConversationSystemMessageInserter(
        dependencies: LegacyConversationDependencies,
    ): SystemMessageInserter = dependencies.systemMessageInserter

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("persistenceEventHookNotifier")
    fun provideConversationPersistenceEventHookNotifier(
        dependencies: LegacyConversationDependencies,
    ): PersistenceEventHookNotifier = dependencies.persistenceEventHookNotifier

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("memberJoinEventHandler")
    fun provideConversationMemberJoinEventHandler(
        dependencies: LegacyConversationDependencies,
    ): MemberJoinEventHandler = dependencies.memberJoinEventHandler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("joinExistingMLSConversation")
    fun provideConversationJoinExistingMLSConversation(
        dependencies: LegacyConversationDependencies,
    ): JoinExistingMLSConversationUseCase = dependencies.joinExistingMLSConversation

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("pendingActionsRepository")
    fun provideConversationPendingActionsRepository(
        dependencies: LegacyConversationDependencies,
    ): PendingActionsRepository = dependencies.pendingActionsRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("dispatcher")
    fun provideConversationDispatcher(
        dependencies: LegacyConversationDependencies,
    ): KaliumDispatcher = dependencies.dispatcher

    @Provides
    @LegacyConversationBinding("getConversations")
    fun provideConversationGetConversations(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): GetConversationsUseCase = GetConversationsUseCase(conversationRepository)

    @Provides
    @LegacyConversationBinding("getConversationDetails")
    fun provideConversationGetConversationDetails(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): GetConversationUseCase = GetConversationUseCase(conversationRepository)

    @Provides
    @LegacyConversationBinding("getOneToOneConversation")
    fun provideConversationGetOneToOneConversation(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): GetOneToOneConversationDetailsUseCase = GetOneToOneConversationDetailsUseCase(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeConversationListDetails")
    fun provideConversationObserveConversationListDetails(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): ObserveConversationListDetailsUseCase = ObserveConversationListDetailsUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeConversationListDetailsWithEvents")
    fun provideConversationObserveConversationListDetailsWithEvents(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("callRepository") callRepository: CallRepository,
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
        @LegacyConversationBinding("getFavoriteFolder") getFavoriteFolder: GetFavoriteFolderUseCase,
    ): ObserveConversationListDetailsWithEventsUseCase = ObserveConversationListDetailsWithEventsUseCaseImpl(
            conversationRepository,
            conversationFolderRepository,
            getFavoriteFolder,
            callRepository
        )

    @Provides
    @LegacyConversationBinding("observeConversationMembers")
    fun provideConversationObserveConversationMembers(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
    ): ObserveConversationMembersUseCase = ObserveConversationMembersUseCaseImpl(conversationRepository, userRepository)

    @Provides
    @LegacyConversationBinding("getMembersToMention")
    fun provideConversationGetMembersToMention(
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("observeConversationMembers") observeConversationMembers: ObserveConversationMembersUseCase,
    ): MembersToMentionUseCase = MembersToMentionUseCase(observeConversationMembers = observeConversationMembers, selfUserId = selfUserId)

    @Provides
    @LegacyConversationBinding("observeEligibleMembersForConversationAdminRole")
    fun provideConversationObserveEligibleMembersForConversationAdminRole(
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("observeConversationMembers") observeConversationMembers: ObserveConversationMembersUseCase,
    ): ObserveEligibleMembersForConversationAdminRoleUseCase = ObserveEligibleMembersForConversationAdminRoleUseCaseImpl(observeConversationMembers, selfUserId)

    @Provides
    @LegacyConversationBinding("observeUserListById")
    fun provideConversationObserveUserListById(
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
    ): ObserveUserListByIdUseCase = ObserveUserListByIdUseCase(userRepository)

    @Provides
    @LegacyConversationBinding("observeConversationDetails")
    fun provideConversationObserveConversationDetails(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): ObserveConversationDetailsUseCase = ObserveConversationDetailsUseCase(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeConversationRoleForUser")
    fun provideConversationObserveConversationRoleForUser(
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
        @LegacyConversationBinding("observeConversationMembers") observeConversationMembers: ObserveConversationMembersUseCase,
        @LegacyConversationBinding("observeConversationDetails") observeConversationDetails: ObserveConversationDetailsUseCase,
    ): ObserveConversationRoleForUserUseCase = ObserveConversationRoleForUserUseCase(
            observeConversationMembers,
            observeConversationDetails,
            ObserveSelfUserUseCaseImpl(userRepository),
        )

    @Provides
    @LegacyConversationBinding("getConversationProtocolInfo")
    fun provideConversationGetConversationProtocolInfo(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): GetConversationProtocolInfoUseCase = GetConversationProtocolInfoUseCase(conversationRepository)

    @Provides
    @LegacyConversationBinding("notifyConversationIsOpen")
    fun provideConversationNotifyConversationIsOpen(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyConversationBinding("deleteEphemeralMessageEndDate") deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase,
        @LegacyConversationBinding("oneOnOneResolver") oneOnOneResolver: OneOnOneResolver,
        @LegacyConversationBinding("kaliumLogger") kaliumLogger: KaliumLogger,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): NotifyConversationIsOpenUseCase = NotifyConversationIsOpenUseCaseImpl(
            oneOnOneResolver,
            conversationRepository,
            deleteEphemeralMessageEndDate,
            slowSyncRepository,
            transactionProvider,
            kaliumLogger
        )

    @Provides
    @LegacyConversationBinding("observeIsSelfUserMemberUseCase")
    fun provideConversationObserveIsSelfUserMemberUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
    ): ObserveIsSelfUserMemberUseCase = ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, selfUserId)

    @Provides
    @LegacyConversationBinding("observeConversationInteractionAvailabilityUseCase")
    fun provideConversationObserveConversationInteractionAvailabilityUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
        @LegacyConversationBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
    ): ObserveConversationInteractionAvailabilityUseCase = ObserveConversationInteractionAvailabilityUseCase(
            conversationRepository,
            selfUserId = selfUserId,
            selfClientIdProvider = currentClientIdProvider,
            userRepository = userRepository
        )

    @Provides
    @LegacyConversationBinding("deleteTeamConversation")
    fun provideConversationDeleteTeamConversation(
        @LegacyConversationBinding("teamRepository") teamRepository: TeamRepository,
        @LegacyConversationBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
        @LegacyConversationBinding("deleteConversationUseCase") deleteConversationUseCase: DeleteConversationUseCase,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): DeleteTeamConversationUseCase = DeleteTeamConversationUseCaseImpl(selfTeamIdProvider, teamRepository, deleteConversationUseCase, transactionProvider)

    @Provides
    @LegacyConversationBinding("createGroupConversation")
    fun provideConversationCreateGroupConversation(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("syncManager") syncManager: SyncManager,
        @LegacyConversationBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyConversationBinding("refreshUsersWithoutMetadata") refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
        @LegacyConversationBinding("newGroupConversationSystemMessagesCreator") newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyConversationBinding("joinExistingMLSConversation") joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
        @LegacyConversationBinding("pendingActionsRepository") pendingActionsRepository: PendingActionsRepository,
    ): GroupConversationCreator = GroupConversationCreatorImpl(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata,
            transactionProvider,
            joinExistingMLSConversation,
            pendingActionsRepository,
        )

    @Provides
    @LegacyConversationBinding("createRegularGroup")
    fun provideConversationCreateRegularGroup(
        @LegacyConversationBinding("createGroupConversation") createGroupConversation: GroupConversationCreator,
    ): CreateRegularGroupUseCase = CreateRegularGroupUseCaseImpl(createGroupConversation)

    @Provides
    @LegacyConversationBinding("createChannel")
    fun provideConversationCreateChannel(
        @LegacyConversationBinding("createGroupConversation") createGroupConversation: GroupConversationCreator,
    ): CreateChannelUseCase = CreateChannelUseCase(createGroupConversation)

    @Provides
    @LegacyConversationBinding("addMemberToConversationUseCase")
    fun provideConversationAddMemberToConversationUseCase(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
        @LegacyConversationBinding("refreshUsersWithoutMetadata") refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
        @LegacyConversationBinding("resetMLSConversationUseCase") resetMLSConversationUseCase: ResetMLSConversationUseCase,
    ): AddMemberToConversationUseCase = AddMemberToConversationUseCaseImpl(
            conversationGroupRepository,
            userRepository,
            refreshUsersWithoutMetadata,
            resetMLSConversationUseCase
        )

    @Provides
    @LegacyConversationBinding("addServiceToConversationUseCase")
    fun provideConversationAddServiceToConversationUseCase(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
    ): AddServiceToConversationUseCase = AddServiceToConversationUseCase(groupRepository = conversationGroupRepository)

    @Provides
    @LegacyConversationBinding("getOrCreateOneToOneConversationUseCase")
    fun provideConversationGetOrCreateOneToOneConversationUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
        @LegacyConversationBinding("oneOnOneResolver") oneOnOneResolver: OneOnOneResolver,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyConversationBinding("checkOneToOneConversationIsReadyUseCase") checkOneToOneConversationIsReadyUseCase: CheckOneToOneConversationIsReadyUseCase,
    ): GetOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCaseImpl(
            conversationRepository,
            userRepository,
            oneOnOneResolver,
            transactionProvider,
            checkOneToOneConversationIsReadyUseCase,
        )

    @Provides
    @LegacyConversationBinding("checkOneToOneConversationIsReadyUseCase")
    fun provideConversationCheckOneToOneConversationIsReadyUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): CheckOneToOneConversationIsReadyUseCase = CheckOneToOneConversationIsReadyUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            transactionProvider,
        )

    @Provides
    @LegacyConversationBinding("isOneToOneConversationCreatedUseCase")
    fun provideConversationIsOneToOneConversationCreatedUseCase(
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
    ): IsOneToOneConversationCreatedUseCase = IsOneToOneConversationCreatedUseCaseImpl(userRepository)

    @Provides
    @LegacyConversationBinding("updateConversationMutedStatus")
    fun provideConversationUpdateConversationMutedStatus(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): UpdateConversationMutedStatusUseCase = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("updateConversationArchivedStatus")
    fun provideConversationUpdateConversationArchivedStatus(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): UpdateConversationArchivedStatusUseCase = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeConnectionList")
    fun provideConversationObserveConnectionList(
        @LegacyConversationBinding("observePendingConnectionRequests") observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase,
    ): ObserveConnectionListUseCase = observePendingConnectionRequests

    @Provides
    @LegacyConversationBinding("observePendingConnectionRequests")
    fun provideConversationObservePendingConnectionRequests(
        @LegacyConversationBinding("connectionRepository") connectionRepository: ConnectionRepository,
    ): ObservePendingConnectionRequestsUseCase = ObservePendingConnectionRequestsUseCaseImpl(connectionRepository)

    @Provides
    @LegacyConversationBinding("markConnectionRequestAsNotified")
    fun provideConversationMarkConnectionRequestAsNotified(
        @LegacyConversationBinding("connectionRepository") connectionRepository: ConnectionRepository,
    ): MarkConnectionRequestAsNotifiedUseCase = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("conversationWorkQueue")
    fun provideConversationConversationWorkQueue(
        @LegacyConversationBinding("scope") scope: CoroutineScope,
        @LegacyConversationBinding("kaliumLogger") kaliumLogger: KaliumLogger,
    ): ConversationWorkQueue = ParallelConversationWorkQueue(scope, kaliumLogger, KaliumDispatcherImpl.default)

    @Provides
    @LegacyConversationBinding("updateConversationReadDateUseCase")
    fun provideConversationUpdateConversationReadDateUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyConversationBinding("messageSender") messageSender: MessageSender,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("selfConversationIdProvider") selfConversationIdProvider: SelfConversationIdProvider,
        @LegacyConversationBinding("sendConfirmation") sendConfirmation: SendConfirmationUseCase,
        @LegacyConversationBinding("kaliumLogger") kaliumLogger: KaliumLogger,
        @LegacyConversationBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
        @LegacyConversationBinding("conversationWorkQueue") conversationWorkQueue: ConversationWorkQueue,
    ): UpdateConversationReadDateUseCase = UpdateConversationReadDateUseCase(
            conversationRepository,
            messageSender,
            currentClientIdProvider,
            selfUserId,
            selfConversationIdProvider,
            sendConfirmation,
            conversationWorkQueue,
            persistenceEventHookNotifier,
            kaliumLogger
        )

    @Provides
    @LegacyConversationBinding("markConversationAsReadLocally")
    fun provideConversationMarkConversationAsReadLocally(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
    ): MarkConversationAsReadLocallyUseCase = MarkConversationAsReadLocallyUseCaseImpl(
            conversationRepository,
            persistenceEventHookNotifier,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("updateConversationAccess")
    fun provideConversationUpdateConversationAccess(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("syncManager") syncManager: SyncManager,
    ): UpdateConversationAccessRoleUseCase = UpdateConversationAccessRoleUseCaseImpl(conversationRepository, conversationGroupRepository, syncManager)

    @Provides
    @LegacyConversationBinding("changeAccessForAppsInConversation")
    fun provideConversationChangeAccessForAppsInConversation(
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("systemMessageInserter") systemMessageInserter: SystemMessageInserter,
        @LegacyConversationBinding("updateConversationAccess") updateConversationAccess: UpdateConversationAccessRoleUseCase,
    ): ChangeAccessForAppsInConversationUseCase = ChangeAccessForAppsInConversationUseCase(updateConversationAccess, systemMessageInserter, selfUserId)

    @Provides
    @LegacyConversationBinding("updateConversationMemberRole")
    fun provideConversationUpdateConversationMemberRole(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): UpdateConversationMemberRoleUseCase = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("removeMemberFromConversation")
    fun provideConversationRemoveMemberFromConversation(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
    ): RemoveMemberFromConversationUseCase = RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository)

    @Provides
    @LegacyConversationBinding("leaveConversation")
    fun provideConversationLeaveConversation(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
    ): LeaveConversationUseCase = LeaveConversationUseCaseImpl(conversationGroupRepository, selfUserId)

    @Provides
    @LegacyConversationBinding("promoteAdminAndLeaveConversation")
    fun provideConversationPromoteAdminAndLeaveConversation(
        @LegacyConversationBinding("updateConversationMemberRole") updateConversationMemberRole: UpdateConversationMemberRoleUseCase,
        @LegacyConversationBinding("leaveConversation") leaveConversation: LeaveConversationUseCase,
    ): PromoteAdminAndLeaveConversationUseCase = PromoteAdminAndLeaveConversationUseCaseImpl(updateConversationMemberRole, leaveConversation)

    @Provides
    @LegacyConversationBinding("checkConversationLeaveConditions")
    fun provideConversationCheckConversationLeaveConditions(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("observeEligibleMembersForConversationAdminRole") observeEligibleMembersForConversationAdminRole: ObserveEligibleMembersForConversationAdminRoleUseCase,
    ): CheckConversationLeaveConditionsUseCase = CheckConversationLeaveConditionsUseCaseImpl(
            conversationRepository,
            observeEligibleMembersForConversationAdminRole,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("renameConversation")
    fun provideConversationRenameConversation(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("persistMessage") persistMessage: PersistMessageUseCase,
        @LegacyConversationBinding("renamedConversationHandler") renamedConversationHandler: RenamedConversationEventHandler,
    ): RenameConversationUseCase = RenameConversationUseCaseImpl(
            conversationRepository,
            persistMessage,
            renamedConversationHandler,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("updateMLSGroupsKeyingMaterials")
    fun provideConversationUpdateMLSGroupsKeyingMaterials(
        @LegacyConversationBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): UpdateKeyingMaterialsUseCase = UpdateKeyingMaterialsUseCaseImpl(mlsConversationRepository, transactionProvider)

    @Provides
    @LegacyConversationBinding("clearConversationAssetsLocally")
    fun provideConversationClearConversationAssetsLocally(
        @LegacyConversationBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyConversationBinding("assetRepository") assetRepository: AssetRepository,
    ): ClearConversationAssetsLocallyUseCase = ClearConversationAssetsLocallyUseCaseImpl(
            messageRepository,
            assetRepository
        )

    @Provides
    @LegacyConversationBinding("clearConversationContent")
    fun provideConversationClearConversationContent(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyConversationBinding("messageSender") messageSender: MessageSender,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("selfConversationIdProvider") selfConversationIdProvider: SelfConversationIdProvider,
        @LegacyConversationBinding("persistenceEventHookNotifier") persistenceEventHookNotifier: PersistenceEventHookNotifier,
        @LegacyConversationBinding("clearConversationAssetsLocally") clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase,
    ): ClearConversationContentUseCase = ClearConversationContentUseCaseImpl(
            conversationRepository,
            messageSender,
            selfUserId,
            currentClientIdProvider,
            selfConversationIdProvider,
            clearConversationAssetsLocally,
            persistenceEventHookNotifier,
        )

    @Provides
    @LegacyConversationBinding("markConversationAsDeletedLocallyUseCase")
    fun provideConversationMarkConversationAsDeletedLocallyUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): MarkConversationAsDeletedLocallyUseCase = MarkConversationAsDeletedLocallyUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("deleteConversationLocallyUseCase")
    fun provideConversationDeleteConversationLocallyUseCase(
        @LegacyConversationBinding("clearConversationContent") clearConversationContent: ClearConversationContentUseCase,
    ): DeleteConversationLocallyUseCase = DeleteConversationLocallyUseCaseImpl(clearConversationContent)

    @Provides
    @LegacyConversationBinding("joinConversationViaCode")
    fun provideConversationJoinConversationViaCode(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyConversationBinding("memberJoinEventHandler") memberJoinEventHandler: MemberJoinEventHandler,
        @LegacyConversationBinding("joinExistingMLSConversation") joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    ): JoinConversationViaCodeUseCase = JoinConversationViaCodeUseCase(
            conversationGroupRepository,
            conversationRepository,
            memberJoinEventHandler,
            joinExistingMLSConversation,
            mlsConversationRepository,
            transactionProvider,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("checkIConversationInviteCode")
    fun provideConversationCheckIConversationInviteCode(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
    ): CheckConversationInviteCodeUseCase = CheckConversationInviteCodeUseCase(
            conversationGroupRepository,
            conversationRepository,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("updateConversationReceiptMode")
    fun provideConversationUpdateConversationReceiptMode(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("persistMessage") persistMessage: PersistMessageUseCase,
    ): UpdateConversationReceiptModeUseCase = UpdateConversationReceiptModeUseCaseImpl(
            conversationRepository = conversationRepository,
            persistMessage = persistMessage,
            selfUserId = selfUserId
        )

    @Provides
    @LegacyConversationBinding("generateGuestRoomLink")
    fun provideConversationGenerateGuestRoomLink(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("userStorage") userStorage: UserStorage,
        @LegacyConversationBinding("serverConfigLinks") serverConfigLinks: ServerConfig.Links,
    ): GenerateGuestRoomLinkUseCase = GenerateGuestRoomLinkUseCaseImpl(
            conversationGroupRepository,
            CodeUpdateHandlerImpl(userStorage.database.conversationDAO, serverConfigLinks)
        )

    @Provides
    @LegacyConversationBinding("revokeGuestRoomLink")
    fun provideConversationRevokeGuestRoomLink(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
    ): RevokeGuestRoomLinkUseCase = RevokeGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    @Provides
    @LegacyConversationBinding("observeGuestRoomLink")
    fun provideConversationObserveGuestRoomLink(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
    ): ObserveGuestRoomLinkUseCase = ObserveGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    @Provides
    @LegacyConversationBinding("updateMessageTimer")
    fun provideConversationUpdateMessageTimer(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
    ): UpdateMessageTimerUseCase = UpdateMessageTimerUseCaseImpl(
            conversationGroupRepository
        )

    @Provides
    @LegacyConversationBinding("getConversationUnreadEventsCountUseCase")
    fun provideConversationGetConversationUnreadEventsCountUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): GetConversationUnreadEventsCountUseCase = GetConversationUnreadEventsCountUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("refreshConversationsWithoutMetadata")
    fun provideConversationRefreshConversationsWithoutMetadata(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("persistConversationsUseCase") persistConversationsUseCase: PersistConversationsUseCase,
        @LegacyConversationBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): RefreshConversationsWithoutMetadataUseCase = RefreshConversationsWithoutMetadataUseCaseImpl(
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase,
            transactionProvider = transactionProvider
        )

    @Provides
    @LegacyConversationBinding("canCreatePasswordProtectedLinks")
    fun provideConversationCanCreatePasswordProtectedLinks(
        @LegacyConversationBinding("selfUserId") selfUserId: UserId,
        @LegacyConversationBinding("serverConfigRepository") serverConfigRepository: ServerConfigRepository,
    ): CanCreatePasswordProtectedLinksUseCase = CanCreatePasswordProtectedLinksUseCase(
            serverConfigRepository,
            selfUserId
        )

    @Provides
    @LegacyConversationBinding("observeArchivedUnreadConversationsCount")
    fun provideConversationObserveArchivedUnreadConversationsCount(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): ObserveArchivedUnreadConversationsCountUseCase = ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("typingIndicatorSenderHandler")
    fun provideConversationTypingIndicatorSenderHandler(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("scope") scope: CoroutineScope,
    ): TypingIndicatorSenderHandler = TypingIndicatorSenderHandlerImpl(conversationRepository = conversationRepository, userSessionCoroutineScope = scope)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("typingIndicatorIncomingRepository")
    fun provideConversationTypingIndicatorIncomingRepository(
        @LegacyConversationBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
    ): TypingIndicatorIncomingRepositoryImpl = TypingIndicatorIncomingRepositoryImpl(
            ConcurrentMutableMap(),
            userPropertyRepository
        )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyConversationBinding("typingIndicatorOutgoingRepository")
    fun provideConversationTypingIndicatorOutgoingRepository(
        @LegacyConversationBinding("userPropertyRepository") userPropertyRepository: UserPropertyRepository,
        @LegacyConversationBinding("typingIndicatorSenderHandler") typingIndicatorSenderHandler: TypingIndicatorSenderHandler,
    ): TypingIndicatorOutgoingRepositoryImpl = TypingIndicatorOutgoingRepositoryImpl(
            typingIndicatorSenderHandler,
            userPropertyRepository
        )

    @Provides
    @LegacyConversationBinding("sendTypingEvent")
    fun provideConversationSendTypingEvent(
        @LegacyConversationBinding("typingIndicatorOutgoingRepository") typingIndicatorOutgoingRepository: TypingIndicatorOutgoingRepositoryImpl,
    ): SendTypingEventUseCase = SendTypingEventUseCaseImpl(typingIndicatorOutgoingRepository)

    @Provides
    @LegacyConversationBinding("observeUsersTyping")
    fun provideConversationObserveUsersTyping(
        @LegacyConversationBinding("userRepository") userRepository: UserRepository,
        @LegacyConversationBinding("typingIndicatorIncomingRepository") typingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl,
    ): ObserveUsersTypingUseCase = ObserveUsersTypingUseCaseImpl(typingIndicatorIncomingRepository, userRepository)

    @Provides
    @LegacyConversationBinding("clearUsersTypingEvents")
    fun provideConversationClearUsersTypingEvents(
        @LegacyConversationBinding("typingIndicatorIncomingRepository") typingIndicatorIncomingRepository: TypingIndicatorIncomingRepositoryImpl,
    ): ClearUsersTypingEventsUseCase = ClearUsersTypingEventsUseCaseImpl(typingIndicatorIncomingRepository)

    @Provides
    @LegacyConversationBinding("setUserInformedAboutVerificationBeforeMessagingUseCase")
    fun provideConversationSetUserInformedAboutVerificationBeforeMessagingUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): SetUserInformedAboutVerificationUseCase = SetUserInformedAboutVerificationUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeInformAboutVerificationBeforeMessagingFlagUseCase")
    fun provideConversationObserveInformAboutVerificationBeforeMessagingFlagUseCase(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): ObserveDegradedConversationNotifiedUseCase = ObserveDegradedConversationNotifiedUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("setNotifiedAboutConversationUnderLegalHold")
    fun provideConversationSetNotifiedAboutConversationUnderLegalHold(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): SetNotifiedAboutConversationUnderLegalHoldUseCase = SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("observeConversationUnderLegalHoldNotified")
    fun provideConversationObserveConversationUnderLegalHoldNotified(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
    ): ObserveConversationUnderLegalHoldNotifiedUseCase = ObserveConversationUnderLegalHoldNotifiedUseCaseImpl(conversationRepository)

    @Provides
    @LegacyConversationBinding("syncConversationCode")
    fun provideConversationSyncConversationCode(
        @LegacyConversationBinding("conversationGroupRepository") conversationGroupRepository: ConversationGroupRepository,
        @LegacyConversationBinding("serverConfigLinks") serverConfigLinks: ServerConfig.Links,
    ): SyncConversationCodeUseCase = SyncConversationCodeUseCase(conversationGroupRepository, serverConfigLinks)

    @Provides
    @LegacyConversationBinding("observeConversationsFromFolder")
    fun provideConversationObserveConversationsFromFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): ObserveConversationsFromFolderUseCase = ObserveConversationsFromFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("getFavoriteFolder")
    fun provideConversationGetFavoriteFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): GetFavoriteFolderUseCase = GetFavoriteFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("addConversationToFavorites")
    fun provideConversationAddConversationToFavorites(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): AddConversationToFavoritesUseCase = AddConversationToFavoritesUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("removeConversationFromFavorites")
    fun provideConversationRemoveConversationFromFavorites(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): RemoveConversationFromFavoritesUseCase = RemoveConversationFromFavoritesUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("observeUserFolders")
    fun provideConversationObserveUserFolders(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): ObserveUserFoldersUseCase = ObserveUserFoldersUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("moveConversationToFolder")
    fun provideConversationMoveConversationToFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): MoveConversationToFolderUseCase = MoveConversationToFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("removeConversationFromFolder")
    fun provideConversationRemoveConversationFromFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): RemoveConversationFromFolderUseCase = RemoveConversationFromFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("createConversationFolder")
    fun provideConversationCreateConversationFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): CreateConversationFolderUseCase = CreateConversationFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("manageConversationFolder")
    fun provideConversationManageConversationFolder(
        @LegacyConversationBinding("conversationFolderRepository") conversationFolderRepository: ConversationFolderRepository,
    ): ManageConversationFolderUseCase = ManageConversationFolderUseCaseImpl(conversationFolderRepository)

    @Provides
    @LegacyConversationBinding("isSelfUserViewerOnConversation")
    fun provideConversationIsSelfUserViewerOnConversation(
        @LegacyConversationBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyConversationBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
    ): IsSelfUserViewerOnConversationUseCase = IsSelfUserViewerOnConversationUseCase(
            conversationRepository = conversationRepository,
            selfTeamIdProvider = selfTeamIdProvider,
        )

}
