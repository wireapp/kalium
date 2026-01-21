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
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
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
import com.wire.kalium.logic.di.UserStorage
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
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList")
public class ConversationScope internal constructor(
    internal val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val conversationFolderRepository: ConversationFolderRepository,
    private val syncManager: SyncManager,
    private val mlsConversationRepository: MLSConversationRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val teamRepository: TeamRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val persistMessage: PersistMessageUseCase,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val sendConfirmation: SendConfirmationUseCase,
    private val renamedConversationHandler: RenamedConversationEventHandler,
    private val serverConfigRepository: ServerConfigRepository,
    private val userStorage: UserStorage,
    userPropertyRepository: UserPropertyRepository,
    private val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase,
    private val oneOnOneResolver: OneOnOneResolver,
    private val scope: CoroutineScope,
    private val kaliumLogger: KaliumLogger,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val serverConfigLinks: ServerConfig.Links,
    internal val messageRepository: MessageRepository,
    internal val assetRepository: AssetRepository,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val persistConversationsUseCase: PersistConversationsUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val resetMLSConversationUseCase: ResetMLSConversationUseCase,
    private val systemMessageInserter: SystemMessageInserter,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    public val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository)

    internal val getConversationDetails: GetConversationUseCase
        get() = GetConversationUseCase(conversationRepository)

    public val getOneToOneConversation: GetOneToOneConversationDetailsUseCase
        get() = GetOneToOneConversationDetailsUseCase(conversationRepository)

    public val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = ObserveConversationListDetailsUseCaseImpl(conversationRepository)

    public val observeConversationListDetailsWithEvents: ObserveConversationListDetailsWithEventsUseCase
        get() = ObserveConversationListDetailsWithEventsUseCaseImpl(conversationRepository, conversationFolderRepository, getFavoriteFolder)

    public val observeConversationMembers: ObserveConversationMembersUseCase
        get() = ObserveConversationMembersUseCaseImpl(conversationRepository, userRepository)

    public val getMembersToMention: MembersToMentionUseCase
        get() = MembersToMentionUseCase(observeConversationMembers = observeConversationMembers, selfUserId = selfUserId)

    public val observeUserListById: ObserveUserListByIdUseCase
        get() = ObserveUserListByIdUseCase(userRepository)

    public val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository)

    public val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
        get() = GetConversationProtocolInfoUseCase(conversationRepository)

    public val notifyConversationIsOpen: NotifyConversationIsOpenUseCase
        get() = NotifyConversationIsOpenUseCaseImpl(
            oneOnOneResolver,
            conversationRepository,
            deleteEphemeralMessageEndDate,
            slowSyncRepository,
            transactionProvider,
            kaliumLogger
        )

    public val observeIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
        get() = ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, selfUserId)

    public val observeConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
        get() = ObserveConversationInteractionAvailabilityUseCase(
            conversationRepository,
            selfUserId = selfUserId,
            selfClientIdProvider = currentClientIdProvider,
            userRepository = userRepository
        )

    public val deleteTeamConversation: DeleteTeamConversationUseCase
        get() = DeleteTeamConversationUseCaseImpl(selfTeamIdProvider, teamRepository, deleteConversationUseCase, transactionProvider)

    internal val createGroupConversation: GroupConversationCreator
        get() = GroupConversationCreatorImpl(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata
        )

    public val createRegularGroup: CreateRegularGroupUseCase
        get() = CreateRegularGroupUseCaseImpl(createGroupConversation)

    public val createChannel: CreateChannelUseCase
        get() = CreateChannelUseCase(createGroupConversation)

    public val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = AddMemberToConversationUseCaseImpl(
            conversationGroupRepository,
            userRepository,
            refreshUsersWithoutMetadata,
            resetMLSConversationUseCase
        )

    public val addServiceToConversationUseCase: AddServiceToConversationUseCase
        get() = AddServiceToConversationUseCase(groupRepository = conversationGroupRepository)

    public val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = GetOrCreateOneToOneConversationUseCaseImpl(
            conversationRepository,
            userRepository,
            oneOnOneResolver,
            transactionProvider
        )

    public val isOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
        get() = IsOneToOneConversationCreatedUseCaseImpl(userRepository)

    public val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    public val updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
        get() = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

    @Deprecated(
        "Name is misleading, and this field will be removed",
        ReplaceWith("observePendingConnectionRequests")
    )
    internal val observeConnectionList: ObserveConnectionListUseCase
        get() = observePendingConnectionRequests

    internal val observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase
        get() = ObservePendingConnectionRequestsUseCaseImpl(connectionRepository)

    public val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    private val conversationWorkQueue: ConversationWorkQueue by lazy {
        ParallelConversationWorkQueue(scope, kaliumLogger, KaliumDispatcherImpl.default)
    }

    public val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
        get() = UpdateConversationReadDateUseCase(
            conversationRepository,
            messageSender,
            currentClientIdProvider,
            selfUserId,
            selfConversationIdProvider,
            sendConfirmation,
            conversationWorkQueue,
            kaliumLogger
        )

    public val updateConversationAccess: UpdateConversationAccessRoleUseCase
        get() = UpdateConversationAccessRoleUseCaseImpl(conversationRepository, conversationGroupRepository, syncManager)

    public val changeAccessForAppsInConversation: ChangeAccessForAppsInConversationUseCase
        get() = ChangeAccessForAppsInConversationUseCase(updateConversationAccess, systemMessageInserter, selfUserId)

    public val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
        get() = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)

    public val removeMemberFromConversation: RemoveMemberFromConversationUseCase
        get() = RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository)

    public val leaveConversation: LeaveConversationUseCase
        get() = LeaveConversationUseCaseImpl(conversationGroupRepository, selfUserId)

    public val renameConversation: RenameConversationUseCase
        get() = RenameConversationUseCaseImpl(
            conversationRepository,
            persistMessage,
            renamedConversationHandler,
            selfUserId
        )

    internal val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
        get() = UpdateKeyingMaterialsUseCaseImpl(mlsConversationRepository, transactionProvider)

    internal val clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase
        get() = ClearConversationAssetsLocallyUseCaseImpl(
            messageRepository,
            assetRepository
        )

    public val clearConversationContent: ClearConversationContentUseCase
        get() = ClearConversationContentUseCaseImpl(
            conversationRepository,
            messageSender,
            selfUserId,
            currentClientIdProvider,
            selfConversationIdProvider,
            clearConversationAssetsLocally
        )

    public val markConversationAsDeletedLocallyUseCase: MarkConversationAsDeletedLocallyUseCase
        get() = MarkConversationAsDeletedLocallyUseCaseImpl(conversationRepository)

    public val deleteConversationLocallyUseCase: DeleteConversationLocallyUseCase
        get() = DeleteConversationLocallyUseCaseImpl(clearConversationContent)

    public val joinConversationViaCode: JoinConversationViaCodeUseCase
        get() = JoinConversationViaCodeUseCase(conversationGroupRepository, selfUserId)

    public val checkIConversationInviteCode: CheckConversationInviteCodeUseCase
        get() = CheckConversationInviteCodeUseCase(
            conversationGroupRepository,
            conversationRepository,
            selfUserId
        )

    public val updateConversationReceiptMode: UpdateConversationReceiptModeUseCase
        get() = UpdateConversationReceiptModeUseCaseImpl(
            conversationRepository = conversationRepository,
            persistMessage = persistMessage,
            selfUserId = selfUserId
        )

    public val generateGuestRoomLink: GenerateGuestRoomLinkUseCase
        get() = GenerateGuestRoomLinkUseCaseImpl(
            conversationGroupRepository,
            CodeUpdateHandlerImpl(userStorage.database.conversationDAO, serverConfigLinks)
        )

    public val revokeGuestRoomLink: RevokeGuestRoomLinkUseCase
        get() = RevokeGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    public val observeGuestRoomLink: ObserveGuestRoomLinkUseCase
        get() = ObserveGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    public val updateMessageTimer: UpdateMessageTimerUseCase
        get() = UpdateMessageTimerUseCaseImpl(
            conversationGroupRepository
        )

    public val getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
        get() = GetConversationUnreadEventsCountUseCaseImpl(conversationRepository)

    public val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
        get() = RefreshConversationsWithoutMetadataUseCaseImpl(
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase,
            transactionProvider = transactionProvider
        )

    public val canCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
        get() = CanCreatePasswordProtectedLinksUseCase(
            serverConfigRepository,
            selfUserId
        )

    public val observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
        get() = ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)

    private val typingIndicatorSenderHandler: TypingIndicatorSenderHandler =
        TypingIndicatorSenderHandlerImpl(conversationRepository = conversationRepository, userSessionCoroutineScope = scope)

    internal val typingIndicatorIncomingRepository =
        TypingIndicatorIncomingRepositoryImpl(
            ConcurrentMutableMap(),
            userPropertyRepository
        )

    internal val typingIndicatorOutgoingRepository =
        TypingIndicatorOutgoingRepositoryImpl(
            typingIndicatorSenderHandler,
            userPropertyRepository
        )

    public val sendTypingEvent: SendTypingEventUseCase
        get() = SendTypingEventUseCaseImpl(typingIndicatorOutgoingRepository)

    public val observeUsersTyping: ObserveUsersTypingUseCase
        get() = ObserveUsersTypingUseCaseImpl(typingIndicatorIncomingRepository, userRepository)

    public val clearUsersTypingEvents: ClearUsersTypingEventsUseCase
        get() = ClearUsersTypingEventsUseCaseImpl(typingIndicatorIncomingRepository)

    public val setUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
        get() = SetUserInformedAboutVerificationUseCaseImpl(conversationRepository)
    public val observeInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
        get() = ObserveDegradedConversationNotifiedUseCaseImpl(conversationRepository)
    public val setNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
        get() = SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)
    public val observeConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
        get() = ObserveConversationUnderLegalHoldNotifiedUseCaseImpl(conversationRepository)
    public val syncConversationCode: SyncConversationCodeUseCase
        get() = SyncConversationCodeUseCase(conversationGroupRepository, serverConfigLinks)
    public val observeConversationsFromFolder: ObserveConversationsFromFolderUseCase
        get() = ObserveConversationsFromFolderUseCaseImpl(conversationFolderRepository)
    public val getFavoriteFolder: GetFavoriteFolderUseCase
        get() = GetFavoriteFolderUseCaseImpl(conversationFolderRepository)
    public val addConversationToFavorites: AddConversationToFavoritesUseCase
        get() = AddConversationToFavoritesUseCaseImpl(conversationFolderRepository)
    public val removeConversationFromFavorites: RemoveConversationFromFavoritesUseCase
        get() = RemoveConversationFromFavoritesUseCaseImpl(conversationFolderRepository)
    public val observeUserFolders: ObserveUserFoldersUseCase
        get() = ObserveUserFoldersUseCaseImpl(conversationFolderRepository)
    public val moveConversationToFolder: MoveConversationToFolderUseCase
        get() = MoveConversationToFolderUseCaseImpl(conversationFolderRepository)
    public val removeConversationFromFolder: RemoveConversationFromFolderUseCase
        get() = RemoveConversationFromFolderUseCaseImpl(conversationFolderRepository)
    public val createConversationFolder: CreateConversationFolderUseCase
        get() = CreateConversationFolderUseCaseImpl(conversationFolderRepository)
}
