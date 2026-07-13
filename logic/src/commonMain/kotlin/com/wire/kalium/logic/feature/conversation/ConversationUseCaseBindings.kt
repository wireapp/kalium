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

@file:Suppress("LargeClass", "LongParameterList", "TooManyFunctions")

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
import com.wire.kalium.logic.di.UserSessionLifetime
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCaseImpl
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
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@BindingContainer
internal object ConversationUseCaseBindings {

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConversationRepository(
        factory: ConversationScopedFactory<ConversationRepository>,
    ): ConversationRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideCallRepository(factory: ConversationScopedFactory<CallRepository>): CallRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConversationGroupRepository(
        factory: ConversationScopedFactory<ConversationGroupRepository>,
    ): ConversationGroupRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConnectionRepository(
        factory: ConversationScopedFactory<ConnectionRepository>,
    ): ConnectionRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideUserRepository(factory: ConversationScopedFactory<UserRepository>): UserRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConversationFolderRepository(
        factory: ConversationScopedFactory<ConversationFolderRepository>,
    ): ConversationFolderRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMLSConversationRepository(
        factory: ConversationScopedFactory<MLSConversationRepository>,
    ): MLSConversationRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMessageSender(factory: ConversationScopedFactory<MessageSender>): MessageSender = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideTeamRepository(factory: ConversationScopedFactory<TeamRepository>): TeamRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideSlowSyncRepository(
        factory: ConversationScopedFactory<SlowSyncRepository>,
    ): SlowSyncRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideServerConfigRepository(
        factory: ConversationScopedFactory<ServerConfigRepository>,
    ): ServerConfigRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideUserPropertyRepository(
        factory: ConversationScopedFactory<UserPropertyRepository>,
    ): UserPropertyRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideOneOnOneResolver(factory: ConversationScopedFactory<OneOnOneResolver>): OneOnOneResolver = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideMessageRepository(
        factory: ConversationScopedFactory<MessageRepository>,
    ): MessageRepository = factory()

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideAssetRepository(factory: ConversationScopedFactory<AssetRepository>): AssetRepository = factory()

    @Provides
    fun provideGetConversations(repository: ConversationRepository): GetConversationsUseCase =
        GetConversationsUseCase(repository)

    @Provides
    fun provideGetConversationDetails(repository: ConversationRepository): GetConversationUseCase =
        GetConversationUseCase(repository)

    @Provides
    fun provideGetOneToOneConversation(repository: ConversationRepository): GetOneToOneConversationDetailsUseCase =
        GetOneToOneConversationDetailsUseCase(repository)

    @Provides
    fun provideObserveConversationListDetails(repository: ConversationRepository): ObserveConversationListDetailsUseCase =
        ObserveConversationListDetailsUseCaseImpl(repository)

    @Provides
    fun provideObserveConversationListDetailsWithEvents(
        conversationRepository: ConversationRepository,
        conversationFolderRepository: ConversationFolderRepository,
        getFavoriteFolder: GetFavoriteFolderUseCase,
        callRepository: CallRepository,
    ): ObserveConversationListDetailsWithEventsUseCase = ObserveConversationListDetailsWithEventsUseCaseImpl(
        conversationRepository,
        conversationFolderRepository,
        getFavoriteFolder,
        callRepository,
    )

    @Provides
    fun provideObserveConversationMembers(
        conversationRepository: ConversationRepository,
        userRepository: UserRepository,
    ): ObserveConversationMembersUseCase = ObserveConversationMembersUseCaseImpl(conversationRepository, userRepository)

    @Provides
    fun provideGetMembersToMention(
        observeConversationMembers: ObserveConversationMembersUseCase,
        selfUserId: UserId,
    ): MembersToMentionUseCase = MembersToMentionUseCase(observeConversationMembers, selfUserId)

    @Provides
    fun provideObserveEligibleMembersForConversationAdminRole(
        observeConversationMembers: ObserveConversationMembersUseCase,
        selfUserId: UserId,
    ): ObserveEligibleMembersForConversationAdminRoleUseCase =
        ObserveEligibleMembersForConversationAdminRoleUseCaseImpl(observeConversationMembers, selfUserId)

    @Provides
    fun provideObserveUserListById(userRepository: UserRepository): ObserveUserListByIdUseCase =
        ObserveUserListByIdUseCase(userRepository)

    @Provides
    fun provideObserveConversationDetails(repository: ConversationRepository): ObserveConversationDetailsUseCase =
        ObserveConversationDetailsUseCase(repository)

    @Provides
    fun provideGetConversationProtocolInfo(repository: ConversationRepository): GetConversationProtocolInfoUseCase =
        GetConversationProtocolInfoUseCase(repository)

    @Provides
    fun provideNotifyConversationIsOpen(
        oneOnOneResolver: OneOnOneResolver,
        conversationRepository: ConversationRepository,
        deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase,
        slowSyncRepository: SlowSyncRepository,
        transactionProvider: CryptoTransactionProvider,
        kaliumLogger: KaliumLogger,
    ): NotifyConversationIsOpenUseCase = NotifyConversationIsOpenUseCaseImpl(
        oneOnOneResolver,
        conversationRepository,
        deleteEphemeralMessageEndDate,
        slowSyncRepository,
        transactionProvider,
        kaliumLogger,
    )

    @Provides
    fun provideObserveIsSelfUserMember(
        conversationRepository: ConversationRepository,
        selfUserId: UserId,
    ): ObserveIsSelfUserMemberUseCase = ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, selfUserId)

    @Provides
    fun provideObserveConversationInteractionAvailability(
        conversationRepository: ConversationRepository,
        selfUserId: UserId,
        currentClientIdProvider: CurrentClientIdProvider,
        userRepository: UserRepository,
    ): ObserveConversationInteractionAvailabilityUseCase = ObserveConversationInteractionAvailabilityUseCase(
        conversationRepository = conversationRepository,
        selfUserId = selfUserId,
        selfClientIdProvider = currentClientIdProvider,
        userRepository = userRepository,
    )

    @Provides
    fun provideDeleteTeamConversation(
        selfTeamIdProvider: SelfTeamIdProvider,
        teamRepository: TeamRepository,
        deleteConversationUseCase: DeleteConversationUseCase,
        transactionProvider: CryptoTransactionProvider,
    ): DeleteTeamConversationUseCase = DeleteTeamConversationUseCaseImpl(
        selfTeamIdProvider,
        teamRepository,
        deleteConversationUseCase,
        transactionProvider,
    )

    @Provides
    fun provideGroupConversationCreator(
        conversationRepository: ConversationRepository,
        conversationGroupRepository: ConversationGroupRepository,
        syncManager: SyncManager,
        currentClientIdProvider: CurrentClientIdProvider,
        systemMessagesCreator: NewGroupConversationSystemMessagesCreator,
        refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    ): GroupConversationCreator = GroupConversationCreatorImpl(
        conversationRepository,
        conversationGroupRepository,
        syncManager,
        currentClientIdProvider,
        systemMessagesCreator,
        refreshUsersWithoutMetadata,
    )

    @Provides
    fun provideCreateRegularGroup(creator: GroupConversationCreator): CreateRegularGroupUseCase =
        CreateRegularGroupUseCaseImpl(creator)

    @Provides
    fun provideCreateChannel(creator: GroupConversationCreator): CreateChannelUseCase = CreateChannelUseCase(creator)

    @Provides
    fun provideAddMemberToConversation(
        conversationGroupRepository: ConversationGroupRepository,
        userRepository: UserRepository,
        refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
        resetMLSConversationUseCase: ResetMLSConversationUseCase,
    ): AddMemberToConversationUseCase = AddMemberToConversationUseCaseImpl(
        conversationGroupRepository,
        userRepository,
        refreshUsersWithoutMetadata,
        resetMLSConversationUseCase,
    )

    @Provides
    fun provideAddServiceToConversation(
        conversationGroupRepository: ConversationGroupRepository,
    ): AddServiceToConversationUseCase = AddServiceToConversationUseCase(conversationGroupRepository)

    @Provides
    fun provideGetOrCreateOneToOneConversation(
        conversationRepository: ConversationRepository,
        userRepository: UserRepository,
        oneOnOneResolver: OneOnOneResolver,
        transactionProvider: CryptoTransactionProvider,
    ): GetOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCaseImpl(
        conversationRepository,
        userRepository,
        oneOnOneResolver,
        transactionProvider,
    )

    @Provides
    fun provideIsOneToOneConversationCreated(userRepository: UserRepository): IsOneToOneConversationCreatedUseCase =
        IsOneToOneConversationCreatedUseCaseImpl(userRepository)

    @Provides
    fun provideUpdateConversationMutedStatus(
        conversationRepository: ConversationRepository,
    ): UpdateConversationMutedStatusUseCase = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    @Provides
    fun provideUpdateConversationArchivedStatus(
        conversationRepository: ConversationRepository,
    ): UpdateConversationArchivedStatusUseCase = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

    @Provides
    fun provideObservePendingConnectionRequests(
        connectionRepository: ConnectionRepository,
    ): ObservePendingConnectionRequestsUseCase = ObservePendingConnectionRequestsUseCaseImpl(connectionRepository)

    @Provides
    fun provideMarkConnectionRequestAsNotified(
        connectionRepository: ConnectionRepository,
    ): MarkConnectionRequestAsNotifiedUseCase = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConversationWorkQueue(
        scope: CoroutineScope,
        kaliumLogger: KaliumLogger,
    ): ConversationWorkQueue = ParallelConversationWorkQueue(scope, kaliumLogger, KaliumDispatcherImpl.default)

    @Provides
    fun provideUpdateConversationReadDate(
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        currentClientIdProvider: CurrentClientIdProvider,
        selfUserId: UserId,
        selfConversationIdProvider: SelfConversationIdProvider,
        sendConfirmation: SendConfirmationUseCase,
        conversationWorkQueue: ConversationWorkQueue,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
        kaliumLogger: KaliumLogger,
    ): UpdateConversationReadDateUseCase = UpdateConversationReadDateUseCase(
        conversationRepository,
        messageSender,
        currentClientIdProvider,
        selfUserId,
        selfConversationIdProvider,
        sendConfirmation,
        conversationWorkQueue,
        persistenceEventHookNotifier,
        kaliumLogger,
    )

    @Provides
    fun provideMarkConversationAsReadLocally(
        conversationRepository: ConversationRepository,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
        selfUserId: UserId,
    ): MarkConversationAsReadLocallyUseCase = MarkConversationAsReadLocallyUseCaseImpl(
        conversationRepository,
        persistenceEventHookNotifier,
        selfUserId,
    )

    @Provides
    fun provideUpdateConversationAccess(
        conversationRepository: ConversationRepository,
        conversationGroupRepository: ConversationGroupRepository,
        syncManager: SyncManager,
    ): UpdateConversationAccessRoleUseCase = UpdateConversationAccessRoleUseCaseImpl(
        conversationRepository,
        conversationGroupRepository,
        syncManager,
    )

    @Provides
    fun provideChangeAccessForAppsInConversation(
        updateConversationAccess: UpdateConversationAccessRoleUseCase,
        systemMessageInserter: SystemMessageInserter,
        selfUserId: UserId,
    ): ChangeAccessForAppsInConversationUseCase = ChangeAccessForAppsInConversationUseCase(
        updateConversationAccess,
        systemMessageInserter,
        selfUserId,
    )

    @Provides
    fun provideUpdateConversationMemberRole(
        conversationRepository: ConversationRepository,
    ): UpdateConversationMemberRoleUseCase = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)

    @Provides
    fun provideRemoveMemberFromConversation(
        conversationGroupRepository: ConversationGroupRepository,
    ): RemoveMemberFromConversationUseCase = RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository)

    @Provides
    fun provideLeaveConversation(
        conversationGroupRepository: ConversationGroupRepository,
        selfUserId: UserId,
    ): LeaveConversationUseCase = LeaveConversationUseCaseImpl(conversationGroupRepository, selfUserId)

    @Provides
    fun providePromoteAdminAndLeaveConversation(
        updateConversationMemberRole: UpdateConversationMemberRoleUseCase,
        leaveConversation: LeaveConversationUseCase,
    ): PromoteAdminAndLeaveConversationUseCase = PromoteAdminAndLeaveConversationUseCaseImpl(
        updateConversationMemberRole,
        leaveConversation,
    )

    @Provides
    fun provideCheckConversationLeaveConditions(
        conversationRepository: ConversationRepository,
        observeEligibleMembers: ObserveEligibleMembersForConversationAdminRoleUseCase,
        selfUserId: UserId,
    ): CheckConversationLeaveConditionsUseCase = CheckConversationLeaveConditionsUseCaseImpl(
        conversationRepository,
        observeEligibleMembers,
        selfUserId,
    )

    @Provides
    fun provideRenameConversation(
        conversationRepository: ConversationRepository,
        persistMessage: PersistMessageUseCase,
        renamedConversationHandler: RenamedConversationEventHandler,
        selfUserId: UserId,
    ): RenameConversationUseCase = RenameConversationUseCaseImpl(
        conversationRepository,
        persistMessage,
        renamedConversationHandler,
        selfUserId,
    )

    @Provides
    fun provideUpdateMLSGroupsKeyingMaterials(
        mlsConversationRepository: MLSConversationRepository,
        transactionProvider: CryptoTransactionProvider,
    ): UpdateKeyingMaterialsUseCase = UpdateKeyingMaterialsUseCaseImpl(mlsConversationRepository, transactionProvider)

    @Provides
    fun provideClearConversationAssetsLocally(
        messageRepository: MessageRepository,
        assetRepository: AssetRepository,
    ): ClearConversationAssetsLocallyUseCase = ClearConversationAssetsLocallyUseCaseImpl(messageRepository, assetRepository)

    @Provides
    fun provideClearConversationContent(
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        selfUserId: UserId,
        currentClientIdProvider: CurrentClientIdProvider,
        selfConversationIdProvider: SelfConversationIdProvider,
        clearConversationAssetsLocally: ClearConversationAssetsLocallyUseCase,
        persistenceEventHookNotifier: PersistenceEventHookNotifier,
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
    fun provideMarkConversationAsDeletedLocally(
        conversationRepository: ConversationRepository,
    ): MarkConversationAsDeletedLocallyUseCase = MarkConversationAsDeletedLocallyUseCaseImpl(conversationRepository)

    @Provides
    fun provideDeleteConversationLocally(
        clearConversationContent: ClearConversationContentUseCase,
    ): DeleteConversationLocallyUseCase = DeleteConversationLocallyUseCaseImpl(clearConversationContent)

    @Provides
    fun provideJoinConversationViaCode(
        conversationGroupRepository: ConversationGroupRepository,
        conversationRepository: ConversationRepository,
        memberJoinEventHandler: MemberJoinEventHandler,
        joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
        mlsConversationRepository: MLSConversationRepository,
        transactionProvider: CryptoTransactionProvider,
        selfUserId: UserId,
    ): JoinConversationViaCodeUseCase = JoinConversationViaCodeUseCase(
        conversationGroupRepository,
        conversationRepository,
        memberJoinEventHandler,
        joinExistingMLSConversation,
        mlsConversationRepository,
        transactionProvider,
        selfUserId,
    )

    @Provides
    fun provideCheckConversationInviteCode(
        conversationGroupRepository: ConversationGroupRepository,
        conversationRepository: ConversationRepository,
        selfUserId: UserId,
    ): CheckConversationInviteCodeUseCase = CheckConversationInviteCodeUseCase(
        conversationGroupRepository,
        conversationRepository,
        selfUserId,
    )

    @Provides
    fun provideUpdateConversationReceiptMode(
        conversationRepository: ConversationRepository,
        persistMessage: PersistMessageUseCase,
        selfUserId: UserId,
    ): UpdateConversationReceiptModeUseCase = UpdateConversationReceiptModeUseCaseImpl(
        conversationRepository,
        persistMessage,
        selfUserId,
    )

    @Provides
    fun provideGenerateGuestRoomLink(
        conversationGroupRepository: ConversationGroupRepository,
        userStorage: UserStorage,
        serverConfigLinks: ServerConfig.Links,
    ): GenerateGuestRoomLinkUseCase = GenerateGuestRoomLinkUseCaseImpl(
        conversationGroupRepository,
        CodeUpdateHandlerImpl(userStorage.database.conversationDAO, serverConfigLinks),
    )

    @Provides
    fun provideRevokeGuestRoomLink(
        conversationGroupRepository: ConversationGroupRepository,
    ): RevokeGuestRoomLinkUseCase = RevokeGuestRoomLinkUseCaseImpl(conversationGroupRepository)

    @Provides
    fun provideObserveGuestRoomLink(
        conversationGroupRepository: ConversationGroupRepository,
    ): ObserveGuestRoomLinkUseCase = ObserveGuestRoomLinkUseCaseImpl(conversationGroupRepository)

    @Provides
    fun provideUpdateMessageTimer(
        conversationGroupRepository: ConversationGroupRepository,
    ): UpdateMessageTimerUseCase = UpdateMessageTimerUseCaseImpl(conversationGroupRepository)

    @Provides
    fun provideGetConversationUnreadEventsCount(
        conversationRepository: ConversationRepository,
    ): GetConversationUnreadEventsCountUseCase = GetConversationUnreadEventsCountUseCaseImpl(conversationRepository)

    @Provides
    fun provideRefreshConversationsWithoutMetadata(
        conversationRepository: ConversationRepository,
        persistConversationsUseCase: PersistConversationsUseCase,
        transactionProvider: CryptoTransactionProvider,
    ): RefreshConversationsWithoutMetadataUseCase = RefreshConversationsWithoutMetadataUseCaseImpl(
        conversationRepository,
        persistConversationsUseCase,
        transactionProvider,
    )

    @Provides
    fun provideCanCreatePasswordProtectedLinks(
        serverConfigRepository: ServerConfigRepository,
        selfUserId: UserId,
    ): CanCreatePasswordProtectedLinksUseCase = CanCreatePasswordProtectedLinksUseCase(
        serverConfigRepository,
        selfUserId,
    )

    @Provides
    fun provideObserveArchivedUnreadConversationsCount(
        conversationRepository: ConversationRepository,
    ): ObserveArchivedUnreadConversationsCountUseCase =
        ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideTypingIndicatorSenderHandler(
        conversationRepository: ConversationRepository,
        scope: CoroutineScope,
    ): TypingIndicatorSenderHandler = TypingIndicatorSenderHandlerImpl(
        conversationRepository = conversationRepository,
        userSessionCoroutineScope = scope,
    )

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideTypingIndicatorIncomingRepository(
        userPropertyRepository: UserPropertyRepository,
    ): TypingIndicatorIncomingRepositoryImpl = TypingIndicatorIncomingRepositoryImpl(
        ConcurrentMutableMap(),
        userPropertyRepository,
    )

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideTypingIndicatorOutgoingRepository(
        typingIndicatorSenderHandler: TypingIndicatorSenderHandler,
        userPropertyRepository: UserPropertyRepository,
    ): TypingIndicatorOutgoingRepositoryImpl = TypingIndicatorOutgoingRepositoryImpl(
        typingIndicatorSenderHandler,
        userPropertyRepository,
    )

    @Provides
    fun provideSendTypingEvent(
        repository: TypingIndicatorOutgoingRepositoryImpl,
    ): SendTypingEventUseCase = SendTypingEventUseCaseImpl(repository)

    @Provides
    fun provideObserveUsersTyping(
        repository: TypingIndicatorIncomingRepositoryImpl,
        userRepository: UserRepository,
    ): ObserveUsersTypingUseCase = ObserveUsersTypingUseCaseImpl(repository, userRepository)

    @Provides
    fun provideClearUsersTypingEvents(
        repository: TypingIndicatorIncomingRepositoryImpl,
    ): ClearUsersTypingEventsUseCase = ClearUsersTypingEventsUseCaseImpl(repository)

    @Provides
    fun provideSetUserInformedAboutVerificationBeforeMessaging(
        conversationRepository: ConversationRepository,
    ): SetUserInformedAboutVerificationUseCase =
        SetUserInformedAboutVerificationUseCaseImpl(conversationRepository)

    @Provides
    fun provideObserveInformAboutVerificationBeforeMessagingFlag(
        conversationRepository: ConversationRepository,
    ): ObserveDegradedConversationNotifiedUseCase = ObserveDegradedConversationNotifiedUseCaseImpl(conversationRepository)

    @Provides
    fun provideSetNotifiedAboutConversationUnderLegalHold(
        conversationRepository: ConversationRepository,
    ): SetNotifiedAboutConversationUnderLegalHoldUseCase =
        SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)

    @Provides
    fun provideObserveConversationUnderLegalHoldNotified(
        conversationRepository: ConversationRepository,
    ): ObserveConversationUnderLegalHoldNotifiedUseCase =
        ObserveConversationUnderLegalHoldNotifiedUseCaseImpl(conversationRepository)

    @Provides
    fun provideSyncConversationCode(
        conversationGroupRepository: ConversationGroupRepository,
        serverConfigLinks: ServerConfig.Links,
    ): SyncConversationCodeUseCase = SyncConversationCodeUseCase(conversationGroupRepository, serverConfigLinks)

    @Provides
    fun provideObserveConversationsFromFolder(
        repository: ConversationFolderRepository,
    ): ObserveConversationsFromFolderUseCase = ObserveConversationsFromFolderUseCaseImpl(repository)

    @Provides
    fun provideGetFavoriteFolder(repository: ConversationFolderRepository): GetFavoriteFolderUseCase =
        GetFavoriteFolderUseCaseImpl(repository)

    @Provides
    fun provideAddConversationToFavorites(
        repository: ConversationFolderRepository,
    ): AddConversationToFavoritesUseCase = AddConversationToFavoritesUseCaseImpl(repository)

    @Provides
    fun provideRemoveConversationFromFavorites(
        repository: ConversationFolderRepository,
    ): RemoveConversationFromFavoritesUseCase = RemoveConversationFromFavoritesUseCaseImpl(repository)

    @Provides
    fun provideObserveUserFolders(repository: ConversationFolderRepository): ObserveUserFoldersUseCase =
        ObserveUserFoldersUseCaseImpl(repository)

    @Provides
    fun provideMoveConversationToFolder(repository: ConversationFolderRepository): MoveConversationToFolderUseCase =
        MoveConversationToFolderUseCaseImpl(repository)

    @Provides
    fun provideRemoveConversationFromFolder(
        repository: ConversationFolderRepository,
    ): RemoveConversationFromFolderUseCase = RemoveConversationFromFolderUseCaseImpl(repository)

    @Provides
    fun provideCreateConversationFolder(repository: ConversationFolderRepository): CreateConversationFolderUseCase =
        CreateConversationFolderUseCaseImpl(repository)

    @Provides
    fun provideIsSelfUserViewerOnConversation(
        conversationRepository: ConversationRepository,
        selfTeamIdProvider: SelfTeamIdProvider,
    ): IsSelfUserViewerOnConversationUseCase = IsSelfUserViewerOnConversationUseCase(
        conversationRepository,
        selfTeamIdProvider,
    )
}
