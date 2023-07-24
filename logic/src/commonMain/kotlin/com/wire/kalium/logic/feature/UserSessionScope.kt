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
import com.wire.kalium.logic.data.client.E2EClientProvider
import com.wire.kalium.logic.data.client.E2EIClientProviderImpl
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
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepositoryImpl
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProviderImpl
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryImpl
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
import com.wire.kalium.logic.data.service.ServiceDataSource
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.team.TeamDataSource
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.AccountRepositoryImpl
import com.wire.kalium.logic.data.user.UserDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.di.PlatformUserStorageProperties
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.asset.ValidateAssetMimeTypeUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetMimeTypeUseCaseImpl
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
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
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdaterImpl
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.ConversationVerificationStatusHandler
import com.wire.kalium.logic.feature.conversation.ConversationVerificationStatusHandlerImpl
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.GetConversationVerificationStatusUseCase
import com.wire.kalium.logic.feature.conversation.GetConversationVerificationStatusUseCaseImpl
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
import com.wire.kalium.logic.feature.e2ei.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.message.DeleteConversationNotificationsManagerImpl
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
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCaseImpl
import com.wire.kalium.logic.feature.service.ServiceScope
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
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.MarkFileSharingChangeAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCaseImpl
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
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatus
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatusImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.network.NetworkStateObserver
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.MissingMetadataUpdateManager
import com.wire.kalium.logic.sync.MissingMetadataUpdateManagerImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SetConnectionPolicyUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
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
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandlerImpl
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
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSWrongEpochHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSWrongEpochHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSlowSyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.SlowSyncWorkerImpl
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
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
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

@Suppress("LongParameterList", "LargeClass")
class UserSessionScope internal constructor(
    userAgent: String,
    private val userId: UserId,
    private val globalScope: GlobalKaliumScope,
    private val globalCallManager: GlobalCallManager,
    private val globalPreferences: GlobalPrefProvider,
    authenticationScopeProvider: AuthenticationScopeProvider,
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val rootPathsProvider: RootPathsProvider,
    dataStoragePaths: DataStoragePaths,
    private val kaliumConfigs: KaliumConfigs,
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

    private val cachedClientIdClearer: CachedClientIdClearer = object : CachedClientIdClearer {
        override fun invoke() {
            _clientId = null
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

    private val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

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

    private val sessionManager: SessionManager = SessionManagerImpl(
        sessionRepository = globalScope.sessionRepository,
        userId = userId,
        tokenStorage = globalPreferences.authTokenStorage,
        logout = { logoutReason -> logout(logoutReason) }
    )
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(
        sessionManager,
        UserIdDTO(userId.value, userId.domain),
        userAgent
    )
    private val featureSupport: FeatureSupport = FeatureSupportImpl(
        kaliumConfigs,
        sessionManager.serverConfig().metaData.commonApiVersion.version
    )
    val authenticationScope: AuthenticationScope = authenticationScopeProvider.provide(
        sessionManager.getServerConfig(),
        sessionManager.getProxyCredentials(),
        globalScope.serverConfigRepository
    )

    private val userConfigRepository: UserConfigRepository
        get() = UserConfigDataSource(userStorage.preferences.userConfigStorage, userStorage.database.userConfigDAO, kaliumConfigs)

    private val userPropertyRepository: UserPropertyRepository
        get() = UserPropertyDataSource(
            authenticatedNetworkContainer.propertiesApi,
            userConfigRepository
        )

    private val keyPackageLimitsProvider: KeyPackageLimitsProvider
        get() = KeyPackageLimitsProviderImpl(kaliumConfigs)

    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider
        get() = UpdateKeyingMaterialThresholdProviderImpl(kaliumConfigs)

    val proteusClientProvider: ProteusClientProvider by lazy {
        ProteusClientProviderImpl(
            rootProteusPath = rootPathsProvider.rootProteusPath(userId),
            userId = userId,
            passphraseStorage = globalPreferences.passphraseStorage,
            kaliumConfigs = kaliumConfigs
        )
    }

    private val mlsClientProvider: MLSClientProvider by lazy {
        MLSClientProviderImpl(
            rootKeyStorePath = rootPathsProvider.rootMLSPath(userId),
            userId = userId,
            currentClientIdProvider = clientIdProvider,
            passphraseStorage = globalPreferences.passphraseStorage
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
            authenticatedNetworkContainer.mlsMessageApi,
            userStorage.database.conversationDAO,
            authenticatedNetworkContainer.clientApi,
            syncManager,
            mlsPublicKeysRepository,
            commitBundleEventReceiver,
            epochsFlow,
            proposalTimersFlow
        )

    private val e2eiRepository: E2EIRepository
        get() = E2EIRepositoryImpl(
            authenticatedNetworkContainer.e2eiApi,
            globalScope.unboundNetworkContainer.acmeApi,
            e2EIClientProvider,
            mlsClientProvider,
            clientIdProvider
        )

    private val e2EIClientProvider: E2EClientProvider by lazy {
        E2EIClientProviderImpl(
            userId = userId,
            currentClientIdProvider = clientIdProvider,
            mlsClientProvider = mlsClientProvider,
            userRepository = userRepository
        )
    }

    val enrollE2EI: EnrollE2EIUseCase get() = EnrollE2EIUseCaseImpl(e2eiRepository)

    private val notificationTokenRepository get() = NotificationTokenDataSource(globalPreferences.tokenStorage)

    private val subconversationRepository = SubconversationRepositoryImpl()

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userId,
            mlsClientProvider,
            selfTeamId,
            userStorage.database.conversationDAO,
            userStorage.database.memberDAO,
            authenticatedNetworkContainer.conversationApi,
            userStorage.database.messageDAO,
            userStorage.database.clientDAO,
            authenticatedNetworkContainer.clientApi,
            userStorage.database.conversationMetaDataDAO
        )

    private val conversationGroupRepository: ConversationGroupRepository
        get() = ConversationGroupRepositoryImpl(
            mlsConversationRepository,
            joinExistingMLSConversationUseCase,
            memberJoinHandler,
            memberLeaveHandler,
            conversationMessageTimerEventHandler,
            userStorage.database.conversationDAO,
            authenticatedNetworkContainer.conversationApi,
            newConversationMembersRepository,
            lazy { conversations.newGroupConversationSystemMessagesCreator },
            userId,
            selfTeamId
        )

    private val newConversationMembersRepository: NewConversationMembersRepository
        get() = NewConversationMembersRepositoryImpl(
            userStorage.database.memberDAO,
            lazy { conversations.newGroupConversationSystemMessagesCreator }
        )

    private val messageRepository: MessageRepository
        get() = MessageDataSource(
            messageApi = authenticatedNetworkContainer.messageApi,
            mlsMessageApi = authenticatedNetworkContainer.mlsMessageApi,
            messageDAO = userStorage.database.messageDAO,
            selfUserId = userId
        )

    private val userRepository: UserRepository = UserDataSource(
        userStorage.database.userDAO,
        userStorage.database.metadataDAO,
        userStorage.database.clientDAO,
        authenticatedNetworkContainer.selfApi,
        authenticatedNetworkContainer.userDetailsApi,
        globalScope.sessionRepository,
        userId,
        qualifiedIdMapper,
        selfTeamId
    )

    private val accountRepository: AccountRepository
        get() = AccountRepositoryImpl(
            userDAO = userStorage.database.userDAO,
            selfUserId = userId,
            selfApi = authenticatedNetworkContainer.selfApi
        )

    internal val pushTokenRepository: PushTokenRepository
        get() = PushTokenDataSource(userStorage.database.metadataDAO)

    private val teamRepository: TeamRepository
        get() = TeamDataSource(
            userStorage.database.userDAO,
            userStorage.database.teamDAO,
            authenticatedNetworkContainer.teamsApi,
            authenticatedNetworkContainer.userDetailsApi,
            userId,
            userStorage.database.serviceDAO
        )

    private val serviceRepository: ServiceRepository
        get() = ServiceDataSource(
            serviceDAO = userStorage.database.serviceDAO
        )

    private val connectionRepository: ConnectionRepository
        get() = ConnectionDataSource(
            userStorage.database.conversationDAO,
            userStorage.database.memberDAO,
            userStorage.database.connectionDAO,
            authenticatedNetworkContainer.connectionApi,
            authenticatedNetworkContainer.userDetailsApi,
            userStorage.database.userDAO,
            userId,
            selfTeamId,
            conversationRepository
        )

    private val userSearchApiWrapper: UserSearchApiWrapper = UserSearchApiWrapperImpl(
        authenticatedNetworkContainer.userSearchApi,
        userStorage.database.conversationDAO,
        userStorage.database.memberDAO,
        userStorage.database.userDAO,
        userStorage.database.metadataDAO
    )

    private val publicUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userStorage.database.userDAO,
            userStorage.database.metadataDAO,
            authenticatedNetworkContainer.userDetailsApi,
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
            callApi = authenticatedNetworkContainer.callApi,
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
            authenticatedNetworkContainer.clientApi,
            clientConfig
        )

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = ClientRegistrationStorageImpl(userStorage.database.metadataDAO)

    internal val clientRepository: ClientRepository
        get() = ClientDataSource(
            clientRemoteRepository,
            clientRegistrationStorage,
            userStorage.database.clientDAO,
            userStorage.database.newClientDAO,
            userId,
            authenticatedNetworkContainer.clientApi,
        )

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(proteusClientProvider, preKeyRepository)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(
            proteusClientProvider = proteusClientProvider, selfUserId = userId
        )

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(
            mlsClientProvider = mlsClientProvider, selfUserId = userId
        )

    private val messageSendingScheduler: MessageSendingScheduler
        get() = userSessionWorkScheduler

    private val assetRepository: AssetRepository
        get() = AssetDataSource(
            assetApi = authenticatedNetworkContainer.assetApi,
            assetDao = userStorage.database.assetDAO,
            kaliumFileSystem = kaliumFileSystem
        )

    private val incrementalSyncRepository: IncrementalSyncRepository by lazy {
        InMemoryIncrementalSyncRepository()
    }

    private val slowSyncRepository: SlowSyncRepository by lazy { SlowSyncRepositoryImpl(userStorage.database.metadataDAO) }

    private val eventGatherer: EventGatherer get() = EventGathererImpl(eventRepository, incrementalSyncRepository)

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

    internal val missingMetadataUpdateManager: MissingMetadataUpdateManager = MissingMetadataUpdateManagerImpl(
        incrementalSyncRepository,
        lazy { users.refreshUsersWithoutMetadata },
        lazy { conversations.refreshConversationsWithoutMetadata },
        lazy { users.timestampKeyRepository }
    )

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
            authenticatedNetworkContainer.conversationApi,
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
            authenticatedNetworkContainer.conversationApi,
            mlsConversationRepository,
            subconversationRepository
        )

    private val leaveSubconversationUseCase: LeaveSubconversationUseCase
        get() = LeaveSubconversationUseCaseImpl(
            authenticatedNetworkContainer.conversationApi,
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
                authenticatedNetworkContainer,
                authenticatedNetworkContainer.accessTokenApi,
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
            authenticatedNetworkContainer.notificationApi, userStorage.database.metadataDAO, clientIdProvider
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
            authenticatedNetworkContainer.mlsPublicKeyApi,
        )

    private val videoStateChecker: VideoStateChecker get() = VideoStateCheckerImpl()

    private val pendingProposalScheduler: PendingProposalScheduler =
        PendingProposalSchedulerImpl(
            kaliumConfigs,
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            lazy { subconversationRepository }
        )

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
            callMapper = callMapper,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            kaliumConfigs = kaliumConfigs
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
            proteusClientProvider = proteusClientProvider, selfUserId = userId
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
            userConfigRepository,
            validateAssetMimeType
        )

    private val applicationMessageHandler: ApplicationMessageHandler
        get() = ApplicationMessageHandlerImpl(
            userRepository,
            messageRepository,
            assetMessageHandler,
            callManager,
            persistMessage,
            persistReaction,
            MessageTextEditHandlerImpl(messageRepository),
            LastReadContentHandlerImpl(conversationRepository, userId, isMessageSentInSelfConversation),
            ClearConversationContentHandlerImpl(
                conversationRepository,
                userId,
                isMessageSentInSelfConversation,
            ),
            DeleteForMeHandlerImpl(messageRepository, isMessageSentInSelfConversation),
            DeleteMessageHandlerImpl(messageRepository, assetRepository, userId),
            messageEncoder,
            receiptMessageHandler,
            userId
        )

    private val mlsWrongEpochHandler: MLSWrongEpochHandler
        get() = MLSWrongEpochHandlerImpl(
            selfUserId = userId,
            persistMessage = persistMessage,
            conversationRepository = conversationRepository,
            joinExistingMLSConversation = joinExistingMLSConversationUseCase
        )

    private val newMessageHandler: NewMessageEventHandler
        get() = NewMessageEventHandlerImpl(
            proteusUnpacker, mlsUnpacker, applicationMessageHandler,
            { conversationId, messageId ->
                messages.ephemeralMessageDeletionHandler.startSelfDeletion(conversationId, messageId)
            }, userId,
            mlsWrongEpochHandler
        )

    private val newConversationHandler: NewConversationEventHandler
        get() = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamId,
            conversations.newGroupConversationSystemMessagesCreator
        )
    private val deletedConversationHandler: DeletedConversationEventHandler
        get() = DeletedConversationEventHandlerImpl(
            userRepository, conversationRepository, DeleteConversationNotificationsManagerImpl
        )
    private val memberJoinHandler: MemberJoinEventHandler
        get() = MemberJoinEventHandlerImpl(
            conversationRepository, userRepository, persistMessage, userId
        )
    private val memberLeaveHandler: MemberLeaveEventHandler
        get() = MemberLeaveEventHandlerImpl(
            userStorage.database.memberDAO, userRepository, persistMessage
        )
    private val memberChangeHandler: MemberChangeEventHandler get() = MemberChangeEventHandlerImpl(conversationRepository)
    private val mlsWelcomeHandler: MLSWelcomeEventHandler
        get() = MLSWelcomeEventHandlerImpl(
            mlsClientProvider, userStorage.database.conversationDAO, conversationRepository
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

    private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler
        get() = ConversationMessageTimerEventHandlerImpl(
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
            receiptModeUpdateEventHandler,
            conversationMessageTimerEventHandler
        )
    }

    private val userEventReceiver: UserEventReceiver
        get() = UserEventReceiverImpl(
            clientRepository, connectionRepository, conversationRepository, userRepository, logout, userId, clientIdProvider
        )

    private val userPropertiesEventReceiver: UserPropertiesEventReceiver
        get() = UserPropertiesEventReceiverImpl(userConfigRepository)

    private val teamEventReceiver: TeamEventReceiver
        get() = TeamEventReceiverImpl(teamRepository, conversationRepository, userRepository, persistMessage, userId)

    private val featureConfigEventReceiver: FeatureConfigEventReceiver
        get() = FeatureConfigEventReceiverImpl(userConfigRepository, kaliumConfigs, userId)

    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            authenticatedNetworkContainer.preKeyApi,
            proteusClientProvider,
            userStorage.database.prekeyDAO,
            userStorage.database.clientDAO
        )

    private val keyPackageRepository: KeyPackageRepository
        get() = KeyPackageDataSource(
            clientIdProvider, authenticatedNetworkContainer.keyPackageApi, mlsClientProvider, userId
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(
        authenticatedNetworkContainer.logoutApi,
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
            pushTokenRepository,
            logoutRepository,
            preKeyRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            mlsClientProvider,
            notificationTokenRepository,
            clientRemoteRepository,
            proteusClientProvider,
            globalScope.sessionRepository,
            upgradeCurrentSessionUseCase,
            userId,
            isAllowedToRegisterMLSClient,
            clientIdProvider,
            userRepository,
            authenticationScope.secondFactorVerificationRepository,
            slowSyncRepository,
            cachedClientIdClearer
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
            proteusClientProvider,
            mlsClientProvider,
            preKeyRepository,
            userRepository,
            userId,
            assetRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            selfConversationIdProvider,
            this
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
            proteusClientProvider,
            mlsClientProvider,
            preKeyRepository,
            userRepository,
            assetRepository,
            reactionRepository,
            receiptRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            userPropertyRepository,
            incrementalSyncRepository,
            protoContentMapper,
            observeSelfDeletingMessages,
            this
        )
    val users: UserScope
        get() = UserScope(
            userRepository,
            accountRepository,
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
            messages.messageSender,
            clientIdProvider,
            team.isSelfATeamMember,
            e2eiRepository
        )
    private val clearUserData: ClearUserDataUseCase get() = ClearUserDataUseCaseImpl(userStorage)

    val validateAssetMimeType: ValidateAssetMimeTypeUseCase get() = ValidateAssetMimeTypeUseCaseImpl()
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
            userSessionWorkScheduler,
            calls.establishedCall,
            calls.endCall,
            kaliumConfigs
        )
    val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = PersistPersistentWebSocketConnectionStatusUseCaseImpl(userId, globalScope.sessionRepository)

    val getPersistentWebSocketStatus: GetPersistentWebSocketStatus
        get() = GetPersistentWebSocketStatusImpl(userId, globalScope.sessionRepository)

    private val featureConfigRepository: FeatureConfigRepository
        get() = FeatureConfigDataSource(
            featureConfigApi = authenticatedNetworkContainer.featureConfigApi
        )
    val isFileSharingEnabled: IsFileSharingEnabledUseCase get() = IsFileSharingEnabledUseCaseImpl(userConfigRepository)
    val observeFileSharingStatus: ObserveFileSharingStatusUseCase
        get() = ObserveFileSharingStatusUseCaseImpl(userConfigRepository)

    val getGuestRoomLinkFeature: GetGuestRoomLinkFeatureStatusUseCase get() = GetGuestRoomLinkFeatureStatusUseCaseImpl(userConfigRepository)

    val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase
        get() = MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)

    val markSelfDeletingMessagesAsNotified: MarkSelfDeletionStatusAsNotifiedUseCase
        get() = MarkSelfDeletionStatusAsNotifiedUseCaseImpl(userConfigRepository)

    val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
        get() = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository, conversationRepository)

    val observeTeamSettingsSelfDeletionStatus: ObserveTeamSettingsSelfDeletingStatusUseCase
        get() = ObserveTeamSettingsSelfDeletingStatusUseCaseImpl(userConfigRepository)

    val persistNewSelfDeletionStatus: PersistNewSelfDeletionTimerUseCaseImpl
        get() = PersistNewSelfDeletionTimerUseCaseImpl(conversationRepository)

    val observeGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase
        get() = ObserveGuestRoomLinkFeatureFlagUseCaseImpl(userConfigRepository)

    val markFileSharingStatusAsNotified: MarkFileSharingChangeAsNotifiedUseCase
        get() = MarkFileSharingChangeAsNotifiedUseCase(userConfigRepository)

    val isMLSEnabled: IsMLSEnabledUseCase get() = IsMLSEnabledUseCaseImpl(featureSupport, userConfigRepository)

    val observeE2EIRequired: ObserveE2EIRequiredUseCase get() = ObserveE2EIRequiredUseCaseImpl(userConfigRepository)
    val markE2EIRequiredAsNotified: MarkEnablingE2EIAsNotifiedUseCase
        get() = MarkEnablingE2EIAsNotifiedUseCaseImpl(userConfigRepository)

    @OptIn(DelicateKaliumApi::class)
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
        get() = IsAllowedToRegisterMLSClientUseCaseImpl(featureSupport, mlsPublicKeysRepository)

    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
        get() = SyncFeatureConfigsUseCaseImpl(
            userConfigRepository, featureConfigRepository, getGuestRoomLinkFeature, kaliumConfigs, userId
        )

    val conversationClientsInCallUpdater: ConversationClientsInCallUpdater
        get() = ConversationClientsInCallUpdaterImpl(
            callManager = callManager,
            conversationRepository = conversationRepository,
            federatedIdMapper = federatedIdMapper
        )

    val team: TeamScope get() = TeamScope(userRepository, teamRepository, conversationRepository, selfTeamId)

    val service: ServiceScope
        get() = ServiceScope(
            serviceRepository,
            teamRepository,
            selfTeamId
        )

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
            conversationClientsInCallUpdater,
            kaliumConfigs
        )

    val connection: ConnectionScope get() = ConnectionScope(connectionRepository, conversationRepository)

    val observeSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase
        get() = ObserveSecurityClassificationLabelUseCaseImpl(
            conversations.observeConversationMembers, conversationRepository, userConfigRepository
        )

    val getOtherUserSecurityClassificationLabel: GetOtherUserSecurityClassificationLabelUseCase
        get() = GetOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository)

    val persistScreenshotCensoringConfig: PersistScreenshotCensoringConfigUseCase
        get() = PersistScreenshotCensoringConfigUseCaseImpl(userConfigRepository = userConfigRepository)

    val observeScreenshotCensoringConfig: ObserveScreenshotCensoringConfigUseCase
        get() = ObserveScreenshotCensoringConfigUseCaseImpl(userConfigRepository = userConfigRepository)

    val kaliumFileSystem: KaliumFileSystem by lazy {
        // Create the cache and asset storage directories
        KaliumFileSystemImpl(dataStoragePaths).also {
            if (!it.exists(dataStoragePaths.cachePath.value.toPath()))
                it.createDirectories(dataStoragePaths.cachePath.value.toPath())
            if (!it.exists(dataStoragePaths.assetStoragePath.value.toPath()))
                it.createDirectories(dataStoragePaths.assetStoragePath.value.toPath())
        }
    }

    private val conversationVerificationStatusHandler: ConversationVerificationStatusHandler
        get() = ConversationVerificationStatusHandlerImpl(
            conversationRepository,
            persistMessage,
            userId
        )

    val getConversationVerificationStatus: GetConversationVerificationStatusUseCase
        get() = GetConversationVerificationStatusUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            conversationVerificationStatusHandler
        )

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

        launch {
            messages.ephemeralMessageDeletionHandler.enqueuePendingSelfDeletionMessages()
        }
    }

    fun onDestroy() {
        cancel()
    }
}

fun interface CachedClientIdClearer {
    operator fun invoke()
}
