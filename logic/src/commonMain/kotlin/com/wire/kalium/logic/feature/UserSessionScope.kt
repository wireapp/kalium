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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly", "konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
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
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProviderImpl
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
import com.wire.kalium.logic.data.conversation.EpochChangesObserverImpl
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepositoryImpl
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProviderImpl
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepositoryDataSource
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryImpl
import com.wire.kalium.logic.data.e2ei.MLSConversationsVerificationStatusesHandler
import com.wire.kalium.logic.data.e2ei.MLSConversationsVerificationStatusesHandlerImpl
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.message.CompositeMessageDataSource
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCaseImpl
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageMetadataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
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
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepositoryImpl
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
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserverImpl
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.asset.ValidateAssetMimeTypeUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetMimeTypeUseCaseImpl
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCaseImpl
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.backup.BackupScope
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManagerImpl
<<<<<<< HEAD
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandler
import com.wire.kalium.logic.feature.conversation.MLSConversationsVerificationStatusesHandlerImpl
=======
>>>>>>> 9233d5fae3 (fix: MLS enabling setting [WPB-7097] (#2643))
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManagerImpl
<<<<<<< HEAD
import com.wire.kalium.logic.feature.conversation.mls.ConversationVerificationStatusCheckerImpl
import com.wire.kalium.logic.feature.conversation.mls.EpochChangesObserverImpl
=======
>>>>>>> 9233d5fae3 (fix: MLS enabling setting [WPB-7097] (#2643))
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolverImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigratorImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolverImpl
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncWorker
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncWorkerImpl
import com.wire.kalium.logic.feature.e2ei.CertificateRevocationListCheckWorker
import com.wire.kalium.logic.feature.e2ei.CertificateRevocationListCheckWorkerImpl
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListForCurrentClientUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListForCurrentClientUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.FeatureFlagSyncWorkerImpl
import com.wire.kalium.logic.feature.featureConfig.FeatureFlagsSyncWorker
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.KeyPackageManagerImpl
import com.wire.kalium.logic.feature.legalhold.ApproveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.ApproveLegalHoldRequestUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.MarkLegalHoldChangeAsNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldChangeNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldRequestUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForSelfUserUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForSelfUserUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCaseImpl
import com.wire.kalium.logic.feature.legalhold.UpdateSelfClientCapabilityToLegalHoldConsentUseCase
import com.wire.kalium.logic.feature.legalhold.UpdateSelfClientCapabilityToLegalHoldConsentUseCaseImpl
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.message.EphemeralEventsNotificationManagerImpl
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCaseImpl
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.StaleEpochVerifierImpl
import com.wire.kalium.logic.feature.migration.MigrationScope
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManagerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorImpl
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefillerImpl
import com.wire.kalium.logic.feature.proteus.ProteusSyncWorker
import com.wire.kalium.logic.feature.proteus.ProteusSyncWorkerImpl
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelectorImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.logic.feature.search.SearchScope
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCaseImpl
import com.wire.kalium.logic.feature.service.ServiceScope
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactoryImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherImpl
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCaseImpl
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCaseImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCaseImpl
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
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCaseImpl
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCaseImpl
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.feature.user.e2ei.MarkNotifyForRevokedCertificateAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.e2ei.MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.e2ei.ObserveShouldNotifyForRevokedCertificateUseCase
import com.wire.kalium.logic.feature.user.e2ei.ObserveShouldNotifyForRevokedCertificateUseCaseImpl
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
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.AvsSyncStateReporterImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.ObserveSyncStateUseCaseImpl
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
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiverImpl
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
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldSystemMessagesHandlerImpl
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.logic.sync.slow.SlowSlowSyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.SlowSyncWorkerImpl
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderImpl
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.firstOrNull
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
    networkStateObserver: NetworkStateObserver,
    private val logoutCallback: LogoutCallback,
) : CoroutineScope {

    private val userStorage = userStorageProvider.getOrCreate(
        userId, platformUserStorageProperties, kaliumConfigs.shouldEncryptData
    )

    private var _clientId: ClientId? = null

    @OptIn(DelicateKaliumApi::class) // Use the uncached client ID in order to create the cache itself.
    private suspend fun clientId(): Either<CoreFailure, ClientId> =
        if (_clientId != null) Either.Right(_clientId!!) else {
            clientRepository.currentClientId().onSuccess {
                _clientId = it
            }
        }

    private val userScopedLogger: KaliumLogger = kaliumLogger.withUserDeviceData {
        KaliumLogger.UserClientData(userId.toLogString(), _clientId?.value?.obfuscateId() ?: "")
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

    val clientIdProvider = CurrentClientIdProvider { clientId() }
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
        // this can depend directly on DAO it will make it easier to user
        // and remove any circular dependency when using this inside user repository
        wrapStorageNullableRequest {
            userStorage.database.userDAO.observeUserDetailsByQualifiedID(userId.toDao()).firstOrNull()
        }.map { userDetailsEntity ->
            _teamId = Either.Right(userDetailsEntity?.team?.let { TeamId(it) })
            userDetailsEntity?.team?.let { TeamId(it) }
        }
    }

    private val selfTeamId = SelfTeamIdProvider { teamId() }

    private val accessTokenRepository: AccessTokenRepository
        get() = AccessTokenRepositoryImpl(
            userId = userId,
            accessTokenApi = authenticatedNetworkContainer.accessTokenApi,
            authTokenStorage = globalPreferences.authTokenStorage
        )

    private val accessTokenRefresherFactory: AccessTokenRefresherFactory
        get() = AccessTokenRefresherFactoryImpl(
            userId = userId,
            tokenStorage = globalPreferences.authTokenStorage
        )

    private val accessTokenRefresher: AccessTokenRefresher
        get() = AccessTokenRefresherImpl(
            repository = accessTokenRepository
        )

    private val sessionManager: SessionManager = SessionManagerImpl(
        sessionRepository = globalScope.sessionRepository,
        accessTokenRefresherFactory = accessTokenRefresherFactory,
        userId = userId,
        tokenStorage = globalPreferences.authTokenStorage,
        logout = { logoutReason -> logout(reason = logoutReason, waitUntilCompletes = true) }
    )
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(
        networkStateObserver,
        sessionManager,
        UserIdDTO(userId.value, userId.domain),
        userAgent,
        certificatePinning = kaliumConfigs.certPinningConfig,
        mockEngine = kaliumConfigs.kaliumMockEngine?.mockEngine,
        kaliumLogger = userScopedLogger
    )
    private val featureSupport: FeatureSupport = FeatureSupportImpl(
        kaliumConfigs,
        sessionManager.serverConfig().metaData.commonApiVersion.version
    )
    val authenticationScope: AuthenticationScope = authenticationScopeProvider.provide(
        sessionManager.getServerConfig(),
        sessionManager.getProxyCredentials(),
        globalScope.serverConfigRepository,
        networkStateObserver,
        kaliumConfigs::certPinningConfig,
        mockEngine = kaliumConfigs.kaliumMockEngine?.mockEngine
    )

    internal val userConfigRepository: UserConfigRepository
        get() = UserConfigDataSource(
            userStorage.preferences.userConfigStorage,
            userStorage.database.userConfigDAO,
            kaliumConfigs
        )

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

    private val checkRevocationList: CheckRevocationListUseCase
        get() = CheckRevocationListUseCaseImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            currentClientIdProvider = clientIdProvider,
            mlsClientProvider = mlsClientProvider,
            mLSConversationsVerificationStatusesHandler = mlsConversationsVerificationStatusesHandler,
            isE2EIEnabledUseCase = isE2EIEnabled
        )
    private val checkRevocationListForCurrentClient: CheckRevocationListForCurrentClientUseCase
        get() = CheckRevocationListForCurrentClientUseCaseImpl(
            checkRevocationList = checkRevocationList,
            certificateRevocationListRepository = certificateRevocationListRepository,
            userConfigRepository = userConfigRepository,
            isE2EIEnabledUseCase = isE2EIEnabled
        )

    private val mlsConversationRepository: MLSConversationRepository
        get() = MLSConversationDataSource(
            userId,
            keyPackageRepository,
            mlsClientProvider,
            authenticatedNetworkContainer.mlsMessageApi,
            userStorage.database.conversationDAO,
            authenticatedNetworkContainer.clientApi,
            syncManager,
            mlsPublicKeysRepository,
            commitBundleEventReceiver,
            epochsFlow,
            proposalTimersFlow,
            keyPackageLimitsProvider,
            checkRevocationList,
            certificateRevocationListRepository,
            sessionManager.getServerConfig().links,
        )

    private val e2eiRepository: E2EIRepository
        get() = E2EIRepositoryImpl(
            authenticatedNetworkContainer.e2eiApi,
            globalScope.unboundNetworkContainer.acmeApi,
            e2EIClientProvider,
            mlsClientProvider,
            clientIdProvider,
            mlsConversationRepository,
            userConfigRepository
        )

    private val e2EIClientProvider: E2EIClientProvider by lazy {
        EI2EIClientProviderImpl(
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
            userRepository,
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

    private val messageMetadataRepository: MessageMetadataRepository
        get() = MessageMetadataSource(messageMetaDataDAO = userStorage.database.messageMetaDataDAO)

    private val compositeMessageRepository: CompositeMessageRepository
        get() = CompositeMessageDataSource(compositeMessageDAO = userStorage.database.compositeMessageDAO)

    private val userRepository: UserRepository = UserDataSource(
        userStorage.database.userDAO,
        userStorage.database.metadataDAO,
        userStorage.database.clientDAO,
        authenticatedNetworkContainer.selfApi,
        authenticatedNetworkContainer.userDetailsApi,
        authenticatedNetworkContainer.teamsApi,
        globalScope.sessionRepository,
        userId,
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
            userStorage.database.userConfigDAO,
            userStorage.database.teamDAO,
            authenticatedNetworkContainer.teamsApi,
            userId,
            userStorage.database.serviceDAO,
            legalHoldHandler,
            legalHoldRequestHandler,
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
            userStorage.database.userDAO,
            conversationRepository
        )

    private val userSearchApiWrapper: UserSearchApiWrapper = UserSearchApiWrapperImpl(
        authenticatedNetworkContainer.userSearchApi,
        userStorage.database.memberDAO,
        userId
    )

    private val searchUserRepository: SearchUserRepository
        get() = SearchUserRepositoryImpl(
            userStorage.database.userDAO,
            userStorage.database.searchDAO,
            authenticatedNetworkContainer.userDetailsApi,
            userSearchApiWrapper,
            userId,
            selfTeamId
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
            epochChangesObserver = epochChangesObserver,
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

    private val eventProcessor: EventProcessor by lazy {
        EventProcessorImpl(
            eventRepository,
            conversationEventReceiver,
            userEventReceiver,
            teamEventReceiver,
            featureConfigEventReceiver,
            userPropertiesEventReceiver,
            federationEventReceiver
        )
    }

    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider
        get() = SlowSlowSyncCriteriaProviderImpl(clientRepository, logoutRepository)

    val syncManager: SyncManager by lazy {
        SyncManagerImpl(
            slowSyncRepository, incrementalSyncRepository
        )
    }

    private val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCaseImpl(
            conversationRepository,
            systemMessageInserter
        )

    private val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository
        )

    private val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCaseImpl(userRepository)
    private val syncContacts: SyncContactsUseCase get() = SyncContactsUseCaseImpl(userRepository)

    private val syncSelfTeamUseCase: SyncSelfTeamUseCase
        get() = SyncSelfTeamUseCaseImpl(
            userRepository = userRepository,
            teamRepository = teamRepository,
            fetchedUsersLimit = kaliumConfigs.limitTeamMembersFetchDuringSlowSync
        )

    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase
        get() = JoinExistingMLSConversationUseCaseImpl(
            featureSupport,
            authenticatedNetworkContainer.conversationApi,
            clientRepository,
            conversationRepository,
            mlsConversationRepository
        )

    private val registerMLSClientUseCase: RegisterMLSClientUseCase
        get() = RegisterMLSClientUseCaseImpl(
            mlsClientProvider,
            clientRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            userConfigRepository
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
            subconversationRepository,
            mlsUnpacker
        )

    private val leaveSubconversationUseCase: LeaveSubconversationUseCase
        get() = LeaveSubconversationUseCaseImpl(
            authenticatedNetworkContainer.conversationApi,
            mlsClientProvider,
            subconversationRepository,
            userId,
            clientIdProvider,
        )

    private val mlsOneOnOneConversationResolver: MLSOneOnOneConversationResolver
        get() = MLSOneOnOneConversationResolverImpl(
            conversationRepository,
            joinExistingMLSConversationUseCase
        )

    private val oneOnOneMigrator: OneOnOneMigrator
        get() = OneOnOneMigratorImpl(
            mlsOneOnOneConversationResolver,
            conversationGroupRepository,
            conversationRepository,
            messageRepository,
            userRepository
        )
    private val oneOnOneResolver: OneOnOneResolver
        get() = OneOnOneResolverImpl(
            userRepository,
            oneOnOneProtocolSelector,
            oneOnOneMigrator,
            incrementalSyncRepository
        )

    private val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase
        get() = UpdateSelfUserSupportedProtocolsUseCaseImpl(
            clientRepository,
            userRepository,
            userConfigRepository,
            featureSupport,
            clientIdProvider,
            userScopedLogger
        )

    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
        get() = UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl(
            updateSupportedProtocols,
            oneOnOneResolver
        )

    private val slowSyncWorker: SlowSyncWorker by lazy {
        SlowSyncWorkerImpl(
            eventRepository,
            syncSelfUser,
            syncFeatureConfigsUseCase,
            updateSupportedProtocols,
            syncConversations,
            syncConnections,
            syncSelfTeamUseCase,
            syncContacts,
            joinExistingMLSConversations,
            fetchLegalHoldForSelfUserFromRemoteUseCase,
            oneOnOneResolver
        )
    }

    private val slowSyncRecoveryHandler: SlowSyncRecoveryHandler
        get() = SlowSyncRecoveryHandlerImpl(logout)

    private val syncMigrationStepsProvider: () -> SyncMigrationStepsProvider = {
        SyncMigrationStepsProviderImpl(lazy { accountRepository }, selfTeamId)
    }

    private val slowSyncManager: SlowSyncManager by lazy {
        SlowSyncManager(
            slowSyncCriteriaProvider,
            slowSyncRepository,
            slowSyncWorker,
            slowSyncRecoveryHandler,
            networkStateObserver,
            syncMigrationStepsProvider

        )
    }
    private val mlsConversationsRecoveryManager: MLSConversationsRecoveryManager by lazy {
        MLSConversationsRecoveryManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            clientRepository,
            recoverMLSConversationsUseCase,
            slowSyncRepository,
            userScopedLogger
        )
    }

    private val conversationsRecoveryManager: ConversationsRecoveryManager by lazy {
        ConversationsRecoveryManagerImpl(
            incrementalSyncRepository,
            addSystemMessageToAllConversationsUseCase,
            slowSyncRepository,
            userScopedLogger
        )
    }

    private val incrementalSyncWorker: IncrementalSyncWorker by lazy {
        IncrementalSyncWorkerImpl(
            eventGatherer,
            eventProcessor
        )
    }
    private val incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandlerImpl
        get() = IncrementalSyncRecoveryHandlerImpl(
            restartSlowSyncProcessForRecoveryUseCase,
            eventRepository,
        )

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
        get() = UpgradeCurrentSessionUseCaseImpl(
            authenticatedNetworkContainer,
            accessTokenRefresher,
            sessionManager
        )

    @Suppress("MagicNumber")
    private val apiMigrations = listOf(
        Pair(3, ApiMigrationV3(clientIdProvider, upgradeCurrentSessionUseCase))
    )

    private val apiMigrationManager
        get() = ApiMigrationManager(
            sessionManager.serverConfig().metaData.commonApiVersion.version,
            userStorage.database.metadataDAO,
            apiMigrations
        )

    private val eventRepository: EventRepository
        get() = EventDataSource(
            authenticatedNetworkContainer.notificationApi, userStorage.database.metadataDAO, clientIdProvider, userId
        )

    private val mlsMigrator: MLSMigrator
        get() = MLSMigratorImpl(
            userId,
            selfTeamId,
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            systemMessageInserter,
            callRepository
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

    val mlsClientManager: MLSClientManager = MLSClientManagerImpl(clientIdProvider,
        isAllowedToRegisterMLSClient,
        incrementalSyncRepository,
        lazy { slowSyncRepository },
        lazy { clientRepository },
        lazy {
            RegisterMLSClientUseCaseImpl(
                mlsClientProvider, clientRepository, keyPackageRepository, keyPackageLimitsProvider, userConfigRepository
            )
        })

    internal val mlsMigrationWorker
        get() =
            MLSMigrationWorkerImpl(
                userConfigRepository,
                featureConfigRepository,
                mlsConfigHandler,
                mlsMigrationConfigHandler,
                mlsMigrator,
            )

    internal val mlsMigrationManager: MLSMigrationManager = MLSMigrationManagerImpl(
        kaliumConfigs,
        featureSupport,
        incrementalSyncRepository,
        lazy { clientRepository },
        lazy { users.timestampKeyRepository },
        lazy { mlsMigrationWorker }
    )

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

    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater
        get() = ConversationClientsInCallUpdaterImpl(
            callManager = callManager,
            conversationRepository = conversationRepository,
            federatedIdMapper = federatedIdMapper
        )

    private val updateConversationClientsForCurrentCall: Lazy<UpdateConversationClientsForCurrentCallUseCase>
        get() = lazy {
            UpdateConversationClientsForCurrentCallUseCaseImpl(callRepository, conversationClientsInCallUpdater)
        }

    private val reactionRepository = ReactionRepositoryImpl(userId, userStorage.database.reactionDAO)
    private val receiptRepository = ReceiptRepositoryImpl(userStorage.database.receiptDAO)
    private val persistReaction: PersistReactionUseCase
        get() = PersistReactionUseCaseImpl(
            reactionRepository
        )

    private val mlsUnpacker: MLSMessageUnpacker
        get() = MLSMessageUnpackerImpl(
            conversationRepository = conversationRepository,
            subconversationRepository = subconversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            pendingProposalScheduler = pendingProposalScheduler,
            selfUserId = userId
        )

    private val proteusUnpacker: ProteusMessageUnpacker
        get() = ProteusMessageUnpackerImpl(
            proteusClientProvider = proteusClientProvider, selfUserId = userId
        )

    private val messageEncoder get() = MessageContentEncoder()

    private val systemMessageInserter get() = SystemMessageInserterImpl(userId, persistMessage)

    private val receiptMessageHandler
        get() = ReceiptMessageHandlerImpl(
            selfUserId = this.userId,
            receiptRepository = receiptRepository,
            messageRepository = messageRepository
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

    private val buttonActionConfirmationHandler: ButtonActionConfirmationHandler
        get() = ButtonActionConfirmationHandlerImpl(compositeMessageRepository, messageMetadataRepository)

    private val applicationMessageHandler: ApplicationMessageHandler
        get() = ApplicationMessageHandlerImpl(
            userRepository,
            messageRepository,
            assetMessageHandler,
            callManager,
            persistMessage,
            persistReaction,
            MessageTextEditHandlerImpl(messageRepository, EphemeralEventsNotificationManagerImpl),
            LastReadContentHandlerImpl(conversationRepository, userId, isMessageSentInSelfConversation),
            ClearConversationContentHandlerImpl(
                conversationRepository,
                userId,
                isMessageSentInSelfConversation,
            ),
            DeleteForMeHandlerImpl(messageRepository, isMessageSentInSelfConversation),
            DeleteMessageHandlerImpl(messageRepository, assetRepository, EphemeralEventsNotificationManagerImpl, userId),
            messageEncoder,
            receiptMessageHandler,
            buttonActionConfirmationHandler,
            userId
        )

    private val staleEpochVerifier: StaleEpochVerifier
        get() = StaleEpochVerifierImpl(
            systemMessageInserter = systemMessageInserter,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            joinExistingMLSConversation = joinExistingMLSConversationUseCase
        )

    private val newMessageHandler: NewMessageEventHandler
        get() = NewMessageEventHandlerImpl(
            proteusUnpacker,
            mlsUnpacker,
            applicationMessageHandler,
            legalHoldHandler,
            { conversationId, messageId ->
                messages.ephemeralMessageDeletionHandler.startSelfDeletion(conversationId, messageId)
            },
            userId,
            staleEpochVerifier
        )

    private val newConversationHandler: NewConversationEventHandler
        get() = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamId,
            conversations.newGroupConversationSystemMessagesCreator,
            oneOnOneResolver,
        )
    private val deletedConversationHandler: DeletedConversationEventHandler
        get() = DeletedConversationEventHandlerImpl(
            userRepository, conversationRepository, EphemeralEventsNotificationManagerImpl
        )
    private val memberJoinHandler: MemberJoinEventHandler
        get() = MemberJoinEventHandlerImpl(
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            persistMessage = persistMessage,
            legalHoldHandler = legalHoldHandler,
            selfUserId = userId
        )
    private val memberLeaveHandler: MemberLeaveEventHandler
        get() = MemberLeaveEventHandlerImpl(
            memberDAO = userStorage.database.memberDAO,
            userRepository = userRepository,
            persistMessage = persistMessage,
            updateConversationClientsForCurrentCall = updateConversationClientsForCurrentCall,
            legalHoldHandler = legalHoldHandler,
            selfTeamIdProvider = selfTeamId
        )
    private val memberChangeHandler: MemberChangeEventHandler
        get() = MemberChangeEventHandlerImpl(
            conversationRepository
        )
    private val mlsWelcomeHandler: MLSWelcomeEventHandler
        get() = MLSWelcomeEventHandlerImpl(
            mlsClientProvider = mlsClientProvider,
            conversationRepository = conversationRepository,
            oneOnOneResolver = oneOnOneResolver,
            refillKeyPackages = client.refillKeyPackages,
            checkRevocationList = checkRevocationList,
            certificateRevocationListRepository = certificateRevocationListRepository
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

    private val conversationCodeUpdateHandler: CodeUpdatedHandler
        get() = CodeUpdateHandlerImpl(
            conversationDAO = userStorage.database.conversationDAO,
            sessionManager.getServerConfig().links
        )

    private val conversationCodeDeletedHandler: CodeDeletedHandler
        get() = CodeDeletedHandlerImpl(
            conversationDAO = userStorage.database.conversationDAO
        )

    private val typingIndicatorHandler: TypingIndicatorHandler
        get() = TypingIndicatorHandlerImpl(userId, conversations.typingIndicatorIncomingRepository)

    private val protocolUpdateEventHandler: ProtocolUpdateEventHandler
        get() = ProtocolUpdateEventHandlerImpl(
            conversationRepository = conversationRepository,
            systemMessageInserter = systemMessageInserter,
            callRepository = callRepository
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
            conversationMessageTimerEventHandler,
            conversationCodeUpdateHandler,
            conversationCodeDeletedHandler,
            typingIndicatorHandler,
            protocolUpdateEventHandler
        )
    }
    override val coroutineContext: CoroutineContext = SupervisorJob()

    private val legalHoldRequestHandler = LegalHoldRequestHandlerImpl(
        selfUserId = userId,
        userConfigRepository = userConfigRepository
    )

    val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase
        get() = ObserveLegalHoldStateForUserUseCaseImpl(clientRepository)

    suspend fun observeIfE2EIRequiredDuringLogin(): Flow<Boolean?> = clientRepository.observeIsClientRegistrationBlockedByE2EI()

    val observeLegalHoldForSelfUser: ObserveLegalHoldStateForSelfUserUseCase
        get() = ObserveLegalHoldStateForSelfUserUseCaseImpl(userId, observeLegalHoldStateForUser, observeLegalHoldRequest)

    val observeLegalHoldChangeNotifiedForSelf: ObserveLegalHoldChangeNotifiedForSelfUseCase
        get() = ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl(userId, userConfigRepository, observeLegalHoldStateForUser)

    val markLegalHoldChangeAsNotifiedForSelf: MarkLegalHoldChangeAsNotifiedForSelfUseCase
        get() = MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl(userConfigRepository)

    val observeLegalHoldRequest: ObserveLegalHoldRequestUseCase
        get() = ObserveLegalHoldRequestUseCaseImpl(
            userConfigRepository = userConfigRepository,
            preKeyRepository = preKeyRepository
        )

    val approveLegalHoldRequest: ApproveLegalHoldRequestUseCase
        get() = ApproveLegalHoldRequestUseCaseImpl(
            teamRepository = teamRepository,
            selfTeamIdProvider = selfTeamId,
        )

    private val fetchSelfClientsFromRemote: FetchSelfClientsFromRemoteUseCase
        get() = FetchSelfClientsFromRemoteUseCaseImpl(
            clientRepository = clientRepository,
            provideClientId = clientIdProvider
        )
    private val fetchUsersClientsFromRemote: FetchUsersClientsFromRemoteUseCase
        get() = FetchUsersClientsFromRemoteUseCaseImpl(
            clientRemoteRepository = clientRemoteRepository,
            clientRepository = clientRepository
        )

    val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase
        get() = MembersHavingLegalHoldClientUseCaseImpl(clientRepository)

    private val legalHoldSystemMessagesHandler = LegalHoldSystemMessagesHandlerImpl(
        selfUserId = userId,
        persistMessage = persistMessage,
        conversationRepository = conversationRepository,
        messageRepository = messageRepository
    )

    private val updateSelfClientCapabilityToLegalHoldConsent: UpdateSelfClientCapabilityToLegalHoldConsentUseCase
        get() = UpdateSelfClientCapabilityToLegalHoldConsentUseCaseImpl(
            clientRemoteRepository = clientRemoteRepository,
            userConfigRepository = userConfigRepository,
            selfClientIdProvider = clientIdProvider,
            incrementalSyncRepository = incrementalSyncRepository,
            kaliumLogger = userScopedLogger,
        )

    private val legalHoldHandler = LegalHoldHandlerImpl(
        selfUserId = userId,
        fetchUsersClientsFromRemote = fetchUsersClientsFromRemote,
        fetchSelfClientsFromRemote = fetchSelfClientsFromRemote,
        observeLegalHoldStateForUser = observeLegalHoldStateForUser,
        membersHavingLegalHoldClient = membersHavingLegalHoldClient,
        userConfigRepository = userConfigRepository,
        conversationRepository = conversationRepository,
        observeSyncState = observeSyncState,
        legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler,
    )

    private val fetchLegalHoldForSelfUserFromRemoteUseCase: FetchLegalHoldForSelfUserFromRemoteUseCase
        get() = FetchLegalHoldForSelfUserFromRemoteUseCaseImpl(
            teamRepository = teamRepository,
            selfTeamIdProvider = selfTeamId,
        )

    private val userEventReceiver: UserEventReceiver
        get() = UserEventReceiverImpl(
            clientRepository,
            connectionRepository,
            userRepository,
            logout,
            oneOnOneResolver,
            userId,
            clientIdProvider,
            lazy { conversations.newGroupConversationSystemMessagesCreator },
            legalHoldRequestHandler,
            legalHoldHandler
        )

    private val userPropertiesEventReceiver: UserPropertiesEventReceiver
        get() = UserPropertiesEventReceiverImpl(userConfigRepository)

    private val federationEventReceiver: FederationEventReceiver
        get() = FederationEventReceiverImpl(
            conversationRepository, connectionRepository, userRepository,
            userStorage.database.memberDAO, persistMessage, userId
        )

    private val teamEventReceiver: TeamEventReceiver
        get() = TeamEventReceiverImpl(userRepository, persistMessage, userId)

    private val guestRoomConfigHandler
        get() = GuestRoomConfigHandler(userConfigRepository, kaliumConfigs)

    private val fileSharingConfigHandler
        get() = FileSharingConfigHandler(userConfigRepository)

    private val mlsConfigHandler
        get() = MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

    private val mlsMigrationConfigHandler
        get() = MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes)

    private val classifiedDomainsConfigHandler
        get() = ClassifiedDomainsConfigHandler(userConfigRepository)

    private val conferenceCallingConfigHandler
        get() = ConferenceCallingConfigHandler(userConfigRepository)

    private val secondFactorPasswordChallengeConfigHandler
        get() = SecondFactorPasswordChallengeConfigHandler(userConfigRepository)

    private val selfDeletingMessagesConfigHandler
        get() = SelfDeletingMessagesConfigHandler(userConfigRepository, kaliumConfigs)

    private val e2eiConfigHandler
        get() = E2EIConfigHandler(userConfigRepository)

    private val appLockConfigHandler
        get() = AppLockConfigHandler(userConfigRepository)

    private val featureConfigEventReceiver: FeatureConfigEventReceiver
        get() = FeatureConfigEventReceiverImpl(
            guestRoomConfigHandler,
            fileSharingConfigHandler,
            mlsConfigHandler,
            mlsMigrationConfigHandler,
            classifiedDomainsConfigHandler,
            conferenceCallingConfigHandler,
            selfDeletingMessagesConfigHandler,
            e2eiConfigHandler,
            appLockConfigHandler
        )

    private val preKeyRepository: PreKeyRepository
        get() = PreKeyDataSource(
            authenticatedNetworkContainer.preKeyApi,
            proteusClientProvider,
            clientIdProvider,
            userStorage.database.prekeyDAO,
            userStorage.database.clientDAO,
            userStorage.database.metadataDAO,
        )
    private val certificateRevocationListRepository: CertificateRevocationListRepository
        get() = CertificateRevocationListRepositoryDataSource(
            acmeApi = globalScope.unboundNetworkContainer.acmeApi,
            metadataDAO = userStorage.database.metadataDAO,
            userConfigRepository = userConfigRepository
        )

    private val proteusPreKeyRefiller: ProteusPreKeyRefiller
        get() = ProteusPreKeyRefillerImpl(preKeyRepository)

    private val proteusSyncWorker: ProteusSyncWorker by lazy {
        ProteusSyncWorkerImpl(
            incrementalSyncRepository = incrementalSyncRepository,
            proteusPreKeyRefiller = proteusPreKeyRefiller,
            preKeyRepository = preKeyRepository,
            kaliumLogger = userScopedLogger,
        )
    }

    private val certificateRevocationListCheckWorker: CertificateRevocationListCheckWorker by lazy {
        CertificateRevocationListCheckWorkerImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            incrementalSyncRepository = incrementalSyncRepository,
            checkRevocationList = checkRevocationList,
            kaliumLogger = userScopedLogger,
        )
    }

    private val featureFlagsSyncWorker: FeatureFlagsSyncWorker by lazy {
        FeatureFlagSyncWorkerImpl(
            incrementalSyncRepository = incrementalSyncRepository,
            syncFeatureConfigs = syncFeatureConfigsUseCase,
            kaliumLogger = userScopedLogger,
        )
    }

    private val keyPackageRepository: KeyPackageRepository
        get() = KeyPackageDataSource(
            clientIdProvider, authenticatedNetworkContainer.keyPackageApi, mlsClientProvider, userId
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(
        authenticatedNetworkContainer.logoutApi,
        userStorage.database.metadataDAO
    )

    val observeSyncState: ObserveSyncStateUseCase
        get() = ObserveSyncStateUseCaseImpl(slowSyncRepository, incrementalSyncRepository)

    private val avsSyncStateReporter: AvsSyncStateReporter by lazy {
        AvsSyncStateReporterImpl(
            callManager = callManager,
            observeSyncStateUseCase = observeSyncState,
            kaliumLogger = userScopedLogger,
        )
    }

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

    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector
        get() = OneOnOneProtocolSelectorImpl(
            userRepository
        )

    private val acmeCertificatesSyncWorker: ACMECertificatesSyncWorker by lazy {
        ACMECertificatesSyncWorkerImpl(
            e2eiRepository = e2eiRepository,
            kaliumLogger = userScopedLogger,
            isE2EIEnabledUseCase = isE2EIEnabled
        )
    }

    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
        get() = RefreshUsersWithoutMetadataUseCaseImpl(
            userRepository
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
            cachedClientIdClearer,
            updateSupportedProtocolsAndResolveOneOnOnes,
            registerMLSClientUseCase,
            syncFeatureConfigsUseCase
        )
    val conversations: ConversationScope by lazy {
        ConversationScope(
            conversationRepository,
            conversationGroupRepository,
            connectionRepository,
            userRepository,
            syncManager,
            mlsConversationRepository,
            clientIdProvider,
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
            globalScope.serverConfigRepository,
            userStorage,
            userPropertyRepository,
            messages.deleteEphemeralMessageEndDate,
            oneOnOneResolver,
            this,
            userScopedLogger,
            refreshUsersWithoutMetadata,
            sessionManager.getServerConfig().links
        )
    }

    val migration get() = MigrationScope(userId, userStorage.database)
    val debug: DebugScope
        get() = DebugScope(
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            clientRepository,
            clientRemoteRepository,
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
            staleEpochVerifier,
            eventProcessor,
            legalHoldHandler,
            this,
            userScopedLogger,
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
            clientRemoteRepository,
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
            messageMetadataRepository,
            staleEpochVerifier,
            legalHoldHandler,
            this,
            userScopedLogger,
        )
    val users: UserScope
        get() = UserScope(
            userRepository,
            userConfigRepository,
            accountRepository,
            searchUserRepository,
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
            e2eiRepository,
            mlsConversationRepository,
            team.isSelfATeamMember,
            updateSupportedProtocols,
            clientRepository,
            joinExistingMLSConversations,
            refreshUsersWithoutMetadata,
            isE2EIEnabled,
            userScopedLogger
        )

    val search: SearchScope
        get() = SearchScope(
            searchUserRepository = searchUserRepository,
            selfUserId = userId,
            sessionRepository = globalScope.sessionRepository,
            kaliumConfigs = kaliumConfigs
        )

    private val clearUserData: ClearUserDataUseCase get() = ClearUserDataUseCaseImpl(userStorage)

    private val validateAssetMimeType: ValidateAssetMimeTypeUseCase get() = ValidateAssetMimeTypeUseCaseImpl()

    val logout: LogoutUseCase
        get() = LogoutUseCaseImpl(
            logoutRepository,
            globalScope.sessionRepository,
            clientRepository,
            userConfigRepository,
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
            logoutCallback,
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

    val observeShouldNotifyForRevokedCertificate: ObserveShouldNotifyForRevokedCertificateUseCase
            by lazy { ObserveShouldNotifyForRevokedCertificateUseCaseImpl(userConfigRepository) }

    val markNotifyForRevokedCertificateAsNotified: MarkNotifyForRevokedCertificateAsNotifiedUseCase
            by lazy { MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl(userConfigRepository) }

    val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase
        get() = MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)

    val appLockTeamFeatureConfigObserver: AppLockTeamFeatureConfigObserver
        get() = AppLockTeamFeatureConfigObserverImpl(userConfigRepository)

    val markTeamAppLockStatusAsNotified: MarkTeamAppLockStatusAsNotifiedUseCase
        get() = MarkTeamAppLockStatusAsNotifiedUseCaseImpl(userConfigRepository)

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
    val isE2EIEnabled: IsE2EIEnabledUseCase get() = IsE2EIEnabledUseCaseImpl(
        userConfigRepository,
        isMLSEnabled
    )

    val getDefaultProtocol: GetDefaultProtocolUseCase
        get() = GetDefaultProtocolUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

    val observeE2EIRequired: ObserveE2EIRequiredUseCase
        get() = ObserveE2EIRequiredUseCaseImpl(
            userConfigRepository,
            featureSupport,
            users.getE2EICertificate,
            clientIdProvider
        )
    val markE2EIRequiredAsNotified: MarkEnablingE2EIAsNotifiedUseCase
        get() = MarkEnablingE2EIAsNotifiedUseCaseImpl(userConfigRepository)

    @OptIn(DelicateKaliumApi::class)
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
        get() = IsAllowedToRegisterMLSClientUseCaseImpl(featureSupport, mlsPublicKeysRepository)

    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
        get() = SyncFeatureConfigsUseCaseImpl(
            featureConfigRepository,
            guestRoomConfigHandler,
            fileSharingConfigHandler,
            mlsConfigHandler,
            mlsMigrationConfigHandler,
            classifiedDomainsConfigHandler,
            conferenceCallingConfigHandler,
            secondFactorPasswordChallengeConfigHandler,
            selfDeletingMessagesConfigHandler,
            e2eiConfigHandler,
            appLockConfigHandler
        )

    val team: TeamScope get() = TeamScope(teamRepository, conversationRepository, selfTeamId)

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

    val connection: ConnectionScope
        get() = ConnectionScope(
            connectionRepository,
            conversationRepository,
            userRepository,
            oneOnOneResolver,
            conversations.newGroupConversationSystemMessagesCreator
        )

    val observeSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase
        get() = ObserveSecurityClassificationLabelUseCaseImpl(
            conversations.observeConversationMembers, conversationRepository, userConfigRepository
        )

    val getOtherUserSecurityClassificationLabel: ObserveOtherUserSecurityClassificationLabelUseCase
        get() = ObserveOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository, userId)

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

    internal val getProxyCredentials: GetProxyCredentialsUseCase
        get() = GetProxyCredentialsUseCaseImpl(sessionManager)

    private fun createPushTokenUpdater() = PushTokenUpdater(
        clientRepository, notificationTokenRepository, pushTokenRepository
    )

    private val epochChangesObserver by lazy { EpochChangesObserverImpl(epochsFlow) }
    private val conversationVerificationStatusChecker by lazy { ConversationVerificationStatusCheckerImpl(mlsClientProvider) }

    private val mlsConversationsVerificationStatusesHandler: MLSConversationsVerificationStatusesHandler by lazy {
        MLSConversationsVerificationStatusesHandlerImpl(
            conversationRepository,
            persistMessage,
            conversationVerificationStatusChecker,
            epochChangesObserver,
            userId,
            userScopedLogger,
        )
    }

    private val typingIndicatorSyncManager: TypingIndicatorSyncManager =
        TypingIndicatorSyncManager(
            typingIndicatorIncomingRepository = lazy { conversations.typingIndicatorIncomingRepository },
            observeSyncStateUseCase = observeSyncState,
            kaliumLogger = userScopedLogger,
        )

    init {
        launch {
            apiMigrationManager.performMigrations()
            // TODO: Add a public start function to the Managers
            incrementalSyncManager
            slowSyncManager

            callRepository.updateOpenCallsToClosedStatus()
            messageRepository.resetAssetTransferStatus()
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

        launch {
            proteusSyncWorker.execute()
        }

        launch {
            certificateRevocationListCheckWorker.execute()
        }

        launch {
            checkRevocationListForCurrentClient.invoke()
        }

        launch {
            avsSyncStateReporter.execute()
        }

        launch {
            mlsConversationsVerificationStatusesHandler.invoke()
        }

        launch {
            typingIndicatorSyncManager.execute()
        }

        launch {
            featureFlagsSyncWorker.execute()
        }

        launch {
            acmeCertificatesSyncWorker.execute()
        }

        launch {
            updateSelfClientCapabilityToLegalHoldConsent()
        }
        launch {
            users.observeCertificateRevocationForSelfClient()
        }
    }

    fun onDestroy() {
        cancel()
    }
}

fun interface CachedClientIdClearer {
    operator fun invoke()
}
