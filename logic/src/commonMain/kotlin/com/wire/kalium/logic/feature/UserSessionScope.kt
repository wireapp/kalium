/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.cache.MLSSelfConversationIdProvider
import com.wire.kalium.logic.cache.MLSSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProviderImpl
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.VideoStateCheckerImpl
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionDataSource
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.CommitBundleEventReceiverImpl
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepositoryImpl
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProviderImpl
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCaseImpl
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepositoryImpl
import com.wire.kalium.logic.data.notification.PushTokenDataSource
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapperImpl
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCaseImpl
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.backup.BackupScope
import com.wire.kalium.logic.feature.backup.CreateBackupUseCase
import com.wire.kalium.logic.feature.backup.RestoreBackupUseCase
import com.wire.kalium.logic.feature.backup.VerifyBackupUseCase
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.NewClientManager
import com.wire.kalium.logic.feature.client.NewClientManagerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ClearConversationContentImpl
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.GetOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.GetOtherUserSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.feature.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.feature.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.feature.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManagerImpl
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.message.EphemeralNotificationsManager
import com.wire.kalium.logic.feature.message.MLSMessageCreator
import com.wire.kalium.logic.feature.message.MLSMessageCreatorImpl
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreator
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreatorImpl
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCaseImpl
import com.wire.kalium.logic.feature.message.SessionEstablisher
import com.wire.kalium.logic.feature.message.SessionEstablisherImpl
import com.wire.kalium.logic.feature.migration.MigrationScope
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCaseImpl
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.MarkFileSharingChangeAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCaseImpl
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.feature.user.guestroomlink.GetGuestRoomLinkFeatureStatusUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.GetGuestRoomLinkFeatureStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.guestroomlink.MarkGuestLinkFeatureFlagAsNotChangedUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl
import com.wire.kalium.logic.feature.user.guestroomlink.ObserveGuestRoomLinkFeatureFlagUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.ObserveGuestRoomLinkFeatureFlagUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatus
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatusImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.network.NetworkStateObserver
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventGathererImpl
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessorImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorkerImpl
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.message.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSlowSyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.SlowSyncWorkerImpl
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import kotlin.coroutines.CoroutineContext

@Suppress("LongParameterList", "LargeClass")
class UserSessionScope internal constructor(
    private val userId: UserId,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet,
    private val globalScope: GlobalKaliumScope,
    private val globalCallManager: GlobalCallManager,
    private val globalPreferences: GlobalPrefProvider,
    private val sessionManager: SessionManager,
    dataStoragePaths: DataStoragePaths,
    private val kaliumConfigs: KaliumConfigs,
    private val featureSupport: FeatureSupport,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    userStorageProvider: UserStorageProvider,
    private val clientConfig: ClientConfig,
    platformUserStorageProperties: PlatformUserStorageProperties,
    networkStateObserver: NetworkStateObserver
) : CoroutineScope {

    private val userStorage = userStorageProvider.getOrCreate(
        userId, platformUserStorageProperties, kaliumConfigs.shouldEncryptData
    )

    private var _clientId: ClientId? = null

    @OptIn(DelicateKaliumApi::class) // Use the uncached client ID in order to create the cache itself.
    private suspend fun clientId(): Either<CoreFailure, ClientId> = if (_clientId != null) Either.Right(_clientId!!) else {
        clientRepository.currentClientId().onSuccess {
            _clientId = it
        }
    }

    val callMapper: CallMapper get() = MapperProvider.callMapper(userId)

    val qualifiedIdMapper: QualifiedIdMapper get() = MapperProvider.qualifiedIdMapper(userId)

    val federatedIdMapper: FederatedIdMapper
        get() = MapperProvider.federatedIdMapper(
            userId, qualifiedIdMapper, globalScope.sessionRepository
        )

    private val clientIdProvider = CurrentClientIdProvider { clientId() }
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider by lazy {
        MLSSelfConversationIdProviderImpl(
            conversationRepository
        )
    }
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider by lazy {
        ProteusSelfConversationIdProviderImpl(
            conversationRepository
        )
    }
    private val selfConversationIdProvider: SelfConversationIdProvider by
    lazy {
        SelfConversationIdProviderImpl(
            clientRepository,
            mlsSelfConversationIdProvider,
            proteusSelfConversationIdProvider
        )
    }

    private val epochsFlow = MutableSharedFlow<GroupID>()

    // TODO(refactor): Extract to Provider class and make atomic
    // val _teamId: Atomic<Either<CoreFailure, TeamId?>> = Atomic(Either.Left(CoreFailure.Unknown(Throwable("NotInitialized"))))
    private var _teamId: Either<CoreFailure, TeamId?> = Either.Left(CoreFailure.Unknown(Throwable("NotInitialized")))

    private suspend fun teamId(): Either<CoreFailure, TeamId?> = if (_teamId.isRight()) _teamId else {
        userRepository.userById(userId).map {
            _teamId = Either.Right(it.teamId)
            it.teamId
        }
    }

    private val selfTeamId = SelfTeamIdProvider { teamId() }

    private val userConfigRepository: UserConfigRepository
        get() = UserConfigDataSource(userStorage.preferences.userConfigStorage)

    private val userPropertyRepository: UserPropertyRepository
        get() = UserPropertyDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.propertiesApi,
            userConfigRepository
        )

    private val keyPackageLimitsProvider: KeyPackageLimitsProvider
        get() = KeyPackageLimitsProviderImpl(kaliumConfigs)

    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider
        get() = UpdateKeyingMaterialThresholdProviderImpl(kaliumConfigs)

    private val mlsClientProvider: MLSClientProvider by lazy {
        MLSClientProviderImpl(
            "${authenticatedDataSourceSet.authenticatedRootDir}/mls", userId, clientIdProvider, globalPreferences.passphraseStorage
        )
    }

    private val commitBundleEventReceiver: CommitBundleEventReceiverImpl
        get() = CommitBundleEventReceiverImpl(
            memberJoinHandler, memberLeaveHandler
        )

    private val mlsConversationRepository: MLSConversationRepository
        get() = MLSConversationDataSource(
            keyPackageRepository,
            mlsClientProvider,
            authenticatedDataSourceSet.authenticatedNetworkContainer.mlsMessageApi,
            userStorage.database.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            syncManager,
            mlsPublicKeysRepository,
            commitBundleEventReceiver,
            epochsFlow
        )

    private val notificationTokenRepository get() = NotificationTokenDataSource(globalPreferences.tokenStorage)

    private val subconversationRepository = SubconversationRepositoryImpl()

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userId,
            mlsClientProvider,
            selfTeamId,
            userStorage.database.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            userStorage.database.messageDAO,
            userStorage.database.clientDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi
        )

    private val conversationGroupRepository: ConversationGroupRepository
        get() = ConversationGroupRepositoryImpl(
            mlsConversationRepository,
            joinExistingMLSConversationUseCase,
            memberJoinHandler,
            memberLeaveHandler,
            userStorage.database.conversationDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            userId,
            selfTeamId
        )

    private val messageRepository: MessageRepository
        get() = MessageDataSource(
            messageApi = authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi,
            mlsMessageApi = authenticatedDataSourceSet.authenticatedNetworkContainer.mlsMessageApi,
            messageDAO = userStorage.database.messageDAO,
            selfUserId = userId
        )

    private val userRepository: UserRepository = UserDataSource(
        userStorage.database.userDAO,
        userStorage.database.metadataDAO,
        userStorage.database.clientDAO,
        authenticatedDataSourceSet.authenticatedNetworkContainer.selfApi,
        authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
        globalScope.sessionRepository,
        userId,
        qualifiedIdMapper,
        selfTeamId,
    )

    internal val pushTokenRepository: PushTokenRepository
        get() = PushTokenDataSource(userStorage.database.metadataDAO)

    private val teamRepository: TeamRepository
        get() = TeamDataSource(
            userStorage.database.userDAO,
            userStorage.database.teamDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.teamsApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userId,
        )

    private val connectionRepository: ConnectionRepository
        get() = ConnectionDataSource(
            userStorage.database.conversationDAO,
            userStorage.database.connectionDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.connectionApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userStorage.database.userDAO,
            userId,
            selfTeamId,
            conversationRepository
        )

    private val userSearchApiWrapper: UserSearchApiWrapper = UserSearchApiWrapperImpl(
        authenticatedDataSourceSet.authenticatedNetworkContainer.userSearchApi,
        userStorage.database.conversationDAO,
        userStorage.database.userDAO,
        userStorage.database.metadataDAO
    )

    private val publicUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userStorage.database.userDAO,
            userStorage.database.metadataDAO,
            authenticatedDataSourceSet.authenticatedNetworkContainer.userDetailsApi,
            userSearchApiWrapper
        )

    val backup: BackupScope
        get() = BackupScope(
            userId,
            clientIdProvider,
            userRepository,
            kaliumFileSystem,
            userStorage,
            persistMigratedMessage,
            restartSlowSyncProcessForRecoveryUseCase,
            globalPreferences,
        )

    @Deprecated("UseCases should be in their respective scopes", ReplaceWith("backup.create"))
    val createBackup: CreateBackupUseCase get() = backup.create

    @Deprecated("UseCases should be in their respective scopes", ReplaceWith("backup.verify"))
    val verifyBackupUseCase: VerifyBackupUseCase get() = backup.verify

    @Deprecated("UseCases should be in their respective scopes", ReplaceWith("backup.restore"))
    val restoreBackup: RestoreBackupUseCase get() = backup.restore

    val persistMessage: PersistMessageUseCase
        get() = PersistMessageUseCaseImpl(messageRepository, userId)

    private val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase
        get() = AddSystemMessageToAllConversationsUseCaseImpl(messageRepository, userId)

    private val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
        get() = RestartSlowSyncProcessForRecoveryUseCaseImpl(slowSyncRepository)

    private val callRepository: CallRepository by lazy {
        CallDataSource(
            callApi = authenticatedDataSourceSet.authenticatedNetworkContainer.callApi,
            qualifiedIdMapper = qualifiedIdMapper,
            callDAO = userStorage.database.callDAO,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            subconversationRepository = subconversationRepository,
            joinSubconversation = joinSubconversationUseCase,
            leaveSubconversation = leaveSubconversationUseCase,
            mlsClientProvider = mlsClientProvider,
            userRepository = userRepository,
            teamRepository = teamRepository,
            persistMessage = persistMessage,
            callMapper = callMapper,
            federatedIdMapper = federatedIdMapper
        )
    }

    private val clientRemoteRepository: ClientRemoteRepository
        get() = ClientRemoteDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi,
            clientConfig
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userStorage.database.metadataDAO)

    private val clientRepository: ClientRepository
        get() = ClientDataSource(
            clientRemoteRepository,
            clientRegistrationStorage,
            userStorage.database.clientDAO,
            userId,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi
        )

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(authenticatedDataSourceSet.proteusClientProvider, preKeyRepository)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(
            proteusClientProvider = authenticatedDataSourceSet.proteusClientProvider, selfUserId = userId
        )

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(
            mlsClientProvider = mlsClientProvider, selfUserId = userId
        )

    private val messageSendingScheduler: MessageSendingScheduler
        get() = authenticatedDataSourceSet.userSessionWorkScheduler

    private val assetRepository: AssetRepository
        get() = AssetDataSource(
            assetApi = authenticatedDataSourceSet.authenticatedNetworkContainer.assetApi,
            assetDao = userStorage.database.assetDAO,
            kaliumFileSystem = kaliumFileSystem
        )

    private val incrementalSyncRepository: IncrementalSyncRepository by lazy {
        InMemoryIncrementalSyncRepository()
    }

    private val slowSyncRepository: SlowSyncRepository by lazy { SlowSyncRepositoryImpl(userStorage.database.metadataDAO) }

    private val eventGatherer: EventGatherer get() = EventGathererImpl(eventRepository, incrementalSyncRepository, slowSyncRepository)

    private val eventProcessor: EventProcessor
        get() = EventProcessorImpl(
            eventRepository,
            conversationEventReceiver,
            userEventReceiver,
            teamEventReceiver,
            featureConfigEventReceiver,
            userPropertiesEventReceiver
        )

    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider
        get() = SlowSlowSyncCriteriaProviderImpl(clientRepository, logoutRepository)

    val syncManager: SyncManager by lazy {
        SyncManagerImpl(
            slowSyncRepository, incrementalSyncRepository
        )
    }

    private val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCase(conversationRepository)

    private val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository
        )

    private val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCase(userRepository)
    private val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)

    private val syncSelfTeamUseCase: SyncSelfTeamUseCase
        get() = SyncSelfTeamUseCaseImpl(
            userRepository = userRepository, teamRepository = teamRepository
        )

    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase
        get() = JoinExistingMLSConversationUseCaseImpl(
            featureSupport,
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            clientRepository,
            conversationRepository,
            mlsConversationRepository
        )

    private val recoverMLSConversationsUseCase: RecoverMLSConversationsUseCase
        get() = RecoverMLSConversationsUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            mlsConversationRepository,
            joinExistingMLSConversationUseCase
        )

    private val joinExistingMLSConversations: JoinExistingMLSConversationsUseCase
        get() = JoinExistingMLSConversationsUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            joinExistingMLSConversationUseCase
        )

    private val joinSubconversationUseCase: JoinSubconversationUseCase
        get() = JoinSubconversationUseCaseImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            mlsConversationRepository,
            subconversationRepository
        )

    private val leaveSubconversationUseCase: LeaveSubconversationUseCase
        get() = LeaveSubconversationUseCaseImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            mlsClientProvider,
            subconversationRepository,
            userId,
            clientIdProvider,
        )

    private val slowSyncWorker: SlowSyncWorker by lazy {
        SlowSyncWorkerImpl(
            syncSelfUser,
            syncFeatureConfigsUseCase,
            syncConversations,
            syncConnections,
            syncSelfTeamUseCase,
            syncContacts,
            joinExistingMLSConversations
        )
    }

    private val slowSyncRecoveryHandler: SlowSyncRecoveryHandler
        get() = SlowSyncRecoveryHandlerImpl(logout)

    private val slowSyncManager: SlowSyncManager by lazy {
        SlowSyncManager(
            slowSyncCriteriaProvider,
            slowSyncRepository,
            slowSyncWorker,
            slowSyncRecoveryHandler,
            networkStateObserver
        )
    }
    private val mlsConversationsRecoveryManager: MLSConversationsRecoveryManager by lazy {
        MLSConversationsRecoveryManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            clientRepository,
            recoverMLSConversationsUseCase,
            slowSyncRepository
        )
    }

    private val conversationsRecoveryManager: ConversationsRecoveryManager by lazy {
        ConversationsRecoveryManagerImpl(
            incrementalSyncRepository,
            addSystemMessageToAllConversationsUseCase,
            slowSyncRepository
        )
    }

    private val incrementalSyncWorker: IncrementalSyncWorker by lazy {
        IncrementalSyncWorkerImpl(
            eventGatherer,
            eventProcessor
        )
    }
    private val incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandlerImpl
        get() = IncrementalSyncRecoveryHandlerImpl(restartSlowSyncProcessForRecoveryUseCase)

    private val incrementalSyncManager by lazy {
        IncrementalSyncManager(
            slowSyncRepository,
            incrementalSyncWorker,
            incrementalSyncRepository,
            incrementalSyncRecoveryHandler,
            networkStateObserver
        )
    }

    private val upgradeCurrentSessionUseCase
        get() =
            UpgradeCurrentSessionUseCaseImpl(
                authenticatedDataSourceSet.authenticatedNetworkContainer,
                authenticatedDataSourceSet.authenticatedNetworkContainer.accessTokenApi,
                sessionManager
            )

    @Suppress("MagicNumber")
    private val apiMigrations = listOf(
        Pair(3, ApiMigrationV3(clientIdProvider, upgradeCurrentSessionUseCase))
    )

    private val apiMigrationManager
        get() = ApiMigrationManager(
            sessionManager.serverConfig().metaData.commonApiVersion.version, userStorage.database.metadataDAO, apiMigrations
        )

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.notificationApi, userStorage.database.metadataDAO, clientIdProvider
        )

    internal val keyPackageManager: KeyPackageManager = KeyPackageManagerImpl(featureSupport,
        incrementalSyncRepository,
        lazy { clientRepository },
        lazy { client.refillKeyPackages },
        lazy { client.mlsKeyPackageCountUseCase },
        lazy { users.timestampKeyRepository })
    internal val keyingMaterialsManager: KeyingMaterialsManager = KeyingMaterialsManagerImpl(featureSupport,
        incrementalSyncRepository,
        lazy { clientRepository },
        lazy { conversations.updateMLSGroupsKeyingMaterials },
        lazy { users.timestampKeyRepository })

    internal val mlsClientManager: MLSClientManager = MLSClientManagerImpl(clientIdProvider,
        isMLSEnabled,
        incrementalSyncRepository,
        lazy { slowSyncRepository },
        lazy { clientRepository },
        lazy {
            RegisterMLSClientUseCaseImpl(
                mlsClientProvider, clientRepository, keyPackageRepository, keyPackageLimitsProvider
            )
        })

    private val mlsPublicKeysRepository: MLSPublicKeysRepository
        get() = MLSPublicKeysRepositoryImpl(
            authenticatedDataSourceSet.authenticatedNetworkContainer.mlsPublicKeyApi,
        )

    private val videoStateChecker: VideoStateChecker get() = VideoStateCheckerImpl()

    private val pendingProposalScheduler: PendingProposalScheduler =
        PendingProposalSchedulerImpl(kaliumConfigs, incrementalSyncRepository, lazy { mlsConversationRepository })

    private val callManager: Lazy<CallManager> = lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            userRepository = userRepository,
            currentClientIdProvider = clientIdProvider,
            conversationRepository = conversationRepository,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = messages.messageSender,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper
        )
    }

    private val flowManagerService by lazy {
        globalCallManager.getFlowManager()
    }

    private val mediaManagerService by lazy {
        globalCallManager.getMediaManager()
    }

    private val reactionRepository = ReactionRepositoryImpl(userId, userStorage.database.reactionDAO)
    private val receiptRepository = ReceiptRepositoryImpl(userStorage.database.receiptDAO)
    private val persistReaction: PersistReactionUseCase
        get() = PersistReactionUseCaseImpl(
            reactionRepository
        )

    private val mlsUnpacker: MLSMessageUnpacker
        get() = MLSMessageUnpackerImpl(
            mlsClientProvider = mlsClientProvider,
            conversationRepository = conversationRepository,
            subconversationRepository = subconversationRepository,
            pendingProposalScheduler = pendingProposalScheduler,
            epochsFlow = epochsFlow,
            selfUserId = userId
        )

    private val proteusUnpacker: ProteusMessageUnpacker
        get() = ProteusMessageUnpackerImpl(
            proteusClientProvider = authenticatedDataSourceSet.proteusClientProvider, selfUserId = userId
        )

    private val messageEncoder get() = MessageContentEncoder()

    private val receiptMessageHandler
        get() = ReceiptMessageHandlerImpl(
            selfUserId = this.userId,
            receiptRepository = receiptRepository
        )

    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase
        get() = IsMessageSentInSelfConversationUseCaseImpl(selfConversationIdProvider)

    private val assetMessageHandler: AssetMessageHandler
        get() = AssetMessageHandlerImpl(
            messageRepository,
            persistMessage,
            userConfigRepository
        )

    private val applicationMessageHandler: ApplicationMessageHandler
        get() = ApplicationMessageHandlerImpl(
            userRepository,
            assetRepository,
            messageRepository,
            assetMessageHandler,
            callManager,
            persistMessage,
            persistReaction,
            MessageTextEditHandlerImpl(messageRepository),
            LastReadContentHandlerImpl(conversationRepository, userId, isMessageSentInSelfConversation),
            ClearConversationContentHandlerImpl(
                ClearConversationContentImpl(conversationRepository, assetRepository),
                userId,
                isMessageSentInSelfConversation,
            ),
            DeleteForMeHandlerImpl(messageRepository, isMessageSentInSelfConversation),
            messageEncoder,
            receiptMessageHandler,
            userId
        )

    private val newMessageHandler: NewMessageEventHandlerImpl
        get() = NewMessageEventHandlerImpl(
            proteusUnpacker, mlsUnpacker, applicationMessageHandler
        )

    private val newConversationHandler: NewConversationEventHandler
        get() = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamId,
            persistMessage,
            qualifiedIdMapper,
            team.isSelfATeamMember
        )
    private val deletedConversationHandler: DeletedConversationEventHandler
        get() = DeletedConversationEventHandlerImpl(
            userRepository, conversationRepository, EphemeralNotificationsManager
        )
    private val memberJoinHandler: MemberJoinEventHandler
        get() = MemberJoinEventHandlerImpl(
            conversationRepository, userRepository, persistMessage
        )
    private val memberLeaveHandler: MemberLeaveEventHandler
        get() = MemberLeaveEventHandlerImpl(
            userStorage.database.conversationDAO, userRepository, persistMessage
        )
    private val memberChangeHandler: MemberChangeEventHandler get() = MemberChangeEventHandlerImpl(conversationRepository)
    private val mlsWelcomeHandler: MLSWelcomeEventHandler
        get() = MLSWelcomeEventHandlerImpl(
            mlsClientProvider, userStorage.database.conversationDAO
        )
    private val renamedConversationHandler: RenamedConversationEventHandler
        get() = RenamedConversationEventHandlerImpl(
            userStorage.database.conversationDAO, persistMessage
        )

    private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler
        get() = ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = userStorage.database.conversationDAO,
            persistMessage = persistMessage
        )

    private val conversationEventReceiver: ConversationEventReceiver by lazy {
        ConversationEventReceiverImpl(
            newMessageHandler,
            newConversationHandler,
            deletedConversationHandler,
            memberJoinHandler,
            memberLeaveHandler,
            memberChangeHandler,
            mlsWelcomeHandler,
            renamedConversationHandler,
            receiptModeUpdateEventHandler
        )
    }

    private val newClientManager: NewClientManager = NewClientManagerImpl

    private val userEventReceiver: UserEventReceiver
        get() = UserEventReceiverImpl(
            newClientManager, connectionRepository, conversationRepository, userRepository, logout, userId, clientIdProvider
        )

    private val userPropertiesEventReceiver: UserPropertiesEventReceiver
        get() = UserPropertiesEventReceiverImpl(userConfigRepository)

    private val teamEventReceiver: TeamEventReceiver
        get() = TeamEventReceiverImpl(teamRepository, conversationRepository, userRepository, persistMessage, userId)

    private val featureConfigEventReceiver: FeatureConfigEventReceiver
        get() = FeatureConfigEventReceiverImpl(userConfigRepository, userRepository, kaliumConfigs, userId)

    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            authenticatedDataSourceSet.authenticatedNetworkContainer.preKeyApi,
            authenticatedDataSourceSet.proteusClientProvider,
            userStorage.database.prekeyDAO,
            userStorage.database.clientDAO
        )

    private val keyPackageRepository: KeyPackageRepository
        get() = KeyPackageDataSource(
            clientIdProvider, authenticatedDataSourceSet.authenticatedNetworkContainer.keyPackageApi, mlsClientProvider, userId
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(
        authenticatedDataSourceSet.authenticatedNetworkContainer.logoutApi,
        userStorage.database.metadataDAO
    )

    val observeSyncState: ObserveSyncStateUseCase
        get() = ObserveSyncStateUseCase(slowSyncRepository, incrementalSyncRepository)

    val setConnectionPolicy: SetConnectionPolicyUseCase
        get() = SetConnectionPolicyUseCase(incrementalSyncRepository)

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl(selfUserId = userId)

    val persistMigratedMessage: PersistMigratedMessagesUseCase
        get() = PersistMigratedMessagesUseCaseImpl(
            userId,
            userStorage.database.migrationDAO,
            protoContentMapper = protoContentMapper
        )

    @OptIn(DelicateKaliumApi::class)
    val client: ClientScope
        get() = ClientScope(
            clientRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            mlsClientProvider,
            notificationTokenRepository,
            clientRemoteRepository,
            authenticatedDataSourceSet.proteusClientProvider,
            globalScope.sessionRepository,
            upgradeCurrentSessionUseCase,
            userId,
            isAllowedToRegisterMLSClient,
            clientIdProvider,
            userRepository,
            authenticatedDataSourceSet.authenticationScope.secondFactorVerificationRepository,
            slowSyncRepository
        )
    val conversations: ConversationScope
        get() = ConversationScope(
            conversationRepository,
            conversationGroupRepository,
            connectionRepository,
            userRepository,
            syncManager,
            mlsConversationRepository,
            clientIdProvider,
            assetRepository,
            messages.messageSender,
            teamRepository,
            userId,
            selfConversationIdProvider,
            persistMessage,
            updateKeyingMaterialThresholdProvider,
            selfTeamId,
            messages.sendConfirmation,
            renamedConversationHandler,
            qualifiedIdMapper,
            team.isSelfATeamMember,
            this
        )

    val migration get() = MigrationScope(userStorage.database)
    val debug: DebugScope
        get() = DebugScope(
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            clientRepository,
            clientIdProvider,
            authenticatedDataSourceSet.proteusClientProvider,
            mlsClientProvider,
            preKeyRepository,
            userRepository,
            userId,
            assetRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            this,
        )
    val messages: MessageScope
        get() = MessageScope(
            connectionRepository,
            userId,
            clientIdProvider,
            selfConversationIdProvider,
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            clientRepository,
            authenticatedDataSourceSet.proteusClientProvider,
            mlsClientProvider,
            preKeyRepository,
            userRepository,
            assetRepository,
            reactionRepository,
            receiptRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            userStorage,
            userPropertyRepository,
            incrementalSyncRepository,
            protoContentMapper,
            this
        )
    val users: UserScope
        get() = UserScope(
            userRepository,
            publicUserRepository,
            syncManager,
            assetRepository,
            teamRepository,
            connectionRepository,
            qualifiedIdMapper,
            globalScope.sessionRepository,
            globalScope.serverConfigRepository,
            userId,
            userStorage.database.metadataDAO,
            userPropertyRepository,
            team.isSelfATeamMember
        )
    private val clearUserData: ClearUserDataUseCase get() = ClearUserDataUseCaseImpl(userStorage)
    val logout: LogoutUseCase
        get() = LogoutUseCaseImpl(
            logoutRepository,
            globalScope.sessionRepository,
            clientRepository,
            userId,
            client.deregisterNativePushToken,
            client.clearClientData,
            clearUserData,
            userSessionScopeProvider,
            pushTokenRepository,
            globalScope,
            authenticatedDataSourceSet.userSessionWorkScheduler,
            kaliumConfigs
        )
    val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = PersistPersistentWebSocketConnectionStatusUseCaseImpl(userId, globalScope.sessionRepository)

    val getPersistentWebSocketStatus: GetPersistentWebSocketStatus
        get() = GetPersistentWebSocketStatusImpl(userId, globalScope.sessionRepository)

    private val featureConfigRepository: FeatureConfigRepository
        get() = FeatureConfigDataSource(
            featureConfigApi = authenticatedDataSourceSet.authenticatedNetworkContainer.featureConfigApi
        )
    val isFileSharingEnabled: IsFileSharingEnabledUseCase get() = IsFileSharingEnabledUseCaseImpl(userConfigRepository)
    val observeFileSharingStatus: ObserveFileSharingStatusUseCase
        get() = ObserveFileSharingStatusUseCaseImpl(userConfigRepository)

    val getGuestRoomLinkFeature: GetGuestRoomLinkFeatureStatusUseCase get() = GetGuestRoomLinkFeatureStatusUseCaseImpl(userConfigRepository)

    val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase
        get() = MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)

    val observeGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase
        get() = ObserveGuestRoomLinkFeatureFlagUseCaseImpl(userConfigRepository)

    val markFileSharingStatusAsNotified: MarkFileSharingChangeAsNotifiedUseCase
        get() = MarkFileSharingChangeAsNotifiedUseCase(userConfigRepository)

    val isMLSEnabled: IsMLSEnabledUseCase get() = IsMLSEnabledUseCaseImpl(featureSupport, userConfigRepository)

    @OptIn(DelicateKaliumApi::class)
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
        get() = IsAllowedToRegisterMLSClientUseCaseImpl(featureSupport, featureConfigRepository, userId)

    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
        get() = SyncFeatureConfigsUseCaseImpl(
            userConfigRepository, featureConfigRepository, isFileSharingEnabled, getGuestRoomLinkFeature, kaliumConfigs, userId
        )

    val team: TeamScope get() = TeamScope(userRepository, teamRepository, conversationRepository, selfTeamId)

    val calls: CallsScope
        get() = CallsScope(
            callManager,
            callRepository,
            conversationRepository,
            userRepository,
            flowManagerService,
            mediaManagerService,
            syncManager,
            qualifiedIdMapper,
            clientIdProvider,
            userConfigRepository,
            kaliumConfigs
        )

    val connection: ConnectionScope get() = ConnectionScope(connectionRepository, conversationRepository)

    val observeSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase
        get() = ObserveSecurityClassificationLabelUseCaseImpl(conversationRepository, userConfigRepository)

    val getOtherUserSecurityClassificationLabel: GetOtherUserSecurityClassificationLabelUseCase
        get() = GetOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository)

    val kaliumFileSystem: KaliumFileSystem by lazy {
        // Create the cache and asset storage directories
        KaliumFileSystemImpl(dataStoragePaths).also {
            if (!it.exists(dataStoragePaths.cachePath.value.toPath()))
                it.createDirectories(dataStoragePaths.cachePath.value.toPath())
            if (!it.exists(dataStoragePaths.assetStoragePath.value.toPath()))
                it.createDirectories(dataStoragePaths.assetStoragePath.value.toPath())
        }
    }

    internal val getProxyCredentials: GetProxyCredentialsUseCase
        get() = GetProxyCredentialsUseCaseImpl(sessionManager)

    private fun createPushTokenUpdater() = PushTokenUpdater(
        clientRepository, notificationTokenRepository, pushTokenRepository
    )

    override val coroutineContext: CoroutineContext = SupervisorJob()

    init {
        launch {
            apiMigrationManager.performMigrations()
            // TODO: Add a public start function to the Managers
            incrementalSyncManager
            slowSyncManager

            callRepository.updateOpenCallsToClosedStatus()
            messageRepository.resetAssetProgressStatus()
        }

        launch {
            val pushTokenUpdater = createPushTokenUpdater()
            pushTokenUpdater.monitorTokenChanges()
        }

        launch {
            mlsConversationsRecoveryManager.invoke()
        }

        launch {
            conversationsRecoveryManager.invoke()
        }
    }

    fun onDestroy() {
        cancel()
    }
}
