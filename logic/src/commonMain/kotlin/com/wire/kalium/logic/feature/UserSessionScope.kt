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

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
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
import com.wire.kalium.logic.data.analytics.AnalyticsDataSource
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.asset.AssetDataSource
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
import com.wire.kalium.logic.data.backup.BackupDataSource
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.InCallReactionsDataSource
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.VideoStateCheckerImpl
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.CryptoTransactionProviderImpl
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProviderImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.MLSTransportProviderImpl
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProviderImpl
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionDataSource
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepositoryImpl
import com.wire.kalium.logic.data.conversation.ConversationMetaDataDataSource
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.EpochChangesObserverImpl
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCaseImpl
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
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCaseImpl
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderDataSource
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepositoryDataSource
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepositoryImpl
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.e2ei.RevocationListCheckerImpl
import com.wire.kalium.logic.data.event.EventDataSource
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
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
import com.wire.kalium.logic.data.message.PersistMessageCallback
import com.wire.kalium.logic.data.message.PersistMessageCallbackManagerImpl
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCaseImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.message.draft.MessageDraftDataSource
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.mls.ConversationProtocolGetterImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandlerImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepositoryImpl
import com.wire.kalium.logic.data.notification.NotificationEventsManagerImpl
import com.wire.kalium.logic.data.notification.PushTokenDataSource
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.ConversationFoldersPropertyDataSource
import com.wire.kalium.logic.data.properties.ReadReceiptsPropertyDataSource
import com.wire.kalium.logic.data.properties.ScreenshotCensoringPropertyDataSource
import com.wire.kalium.logic.data.properties.TypingIndicatorPropertyDataSource
import com.wire.kalium.logic.data.properties.UserPropertiesSyncDataSource
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
import com.wire.kalium.logic.data.sync.SlowSyncStatus
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
import com.wire.kalium.logic.di.UserConfigStorageFactory
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.analytics.AnalyticsIdentifierManager
import com.wire.kalium.logic.feature.analytics.GetAnalyticsContactsDataUseCase
import com.wire.kalium.logic.feature.analytics.GetCurrentAnalyticsTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.analytics.ObserveAnalyticsTrackingIdentifierStatusUseCase
import com.wire.kalium.logic.feature.analytics.SetNewUserTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserverImpl
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCaseImpl
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.backup.BackupScope
import com.wire.kalium.logic.feature.backup.MultiPlatformBackupScope
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.CallBackgroundManagerImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdaterImpl
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProviderImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.channels.ChannelsScope
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.IsAllowedToUseAsyncNotificationsUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToUseAsyncNotificationsUseCaseImpl
import com.wire.kalium.logic.feature.client.MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.MLSClientManagerImpl
import com.wire.kalium.logic.feature.client.ProteusMigrationRecoveryHandlerImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCaseImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManagerImpl
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolverImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigratorImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolverImpl
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.CheckCrlRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.AppsFeatureHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConsumableNotificationsConfigHandler
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
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.StaleEpochVerifierImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManagerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorImpl
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefillerImpl
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelectorImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.logic.feature.search.SearchScope
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCaseImpl
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
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
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCase
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCaseImpl
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
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCaseImpl
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
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.AvsSyncStateReporterImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.ObserveSyncStateUseCaseImpl
import com.wire.kalium.logic.sync.PendingMessagesSenderWorker
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncExecutorImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventGathererImpl
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessorImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorkerImpl
import com.wire.kalium.logic.sync.local.LocalEventManagerImpl
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.local.LocalEventRepositoryImpl
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorkerImpl
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
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorker
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorkerImpl
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandlerImpl
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
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandlerImpl
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
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.work.LongWorkScope
import io.ktor.client.HttpClient
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okio.Path.Companion.toPath
import kotlin.coroutines.CoroutineContext
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@Suppress("LongParameterList", "LargeClass")
public class UserSessionScope internal constructor(
    userAgent: String,
    internal val userId: UserId,
    private val globalScope: GlobalKaliumScope,
    private val globalCallManager: GlobalCallManager,
    private val globalDatabaseBuilder: GlobalDatabaseBuilder,
    private val globalPreferences: GlobalPrefProvider,
    authenticationScopeProvider: AuthenticationScopeProvider,
    private val rootPathsProvider: RootPathsProvider,
    dataStoragePaths: DataStoragePaths,
    private val kaliumConfigs: KaliumConfigs,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    userStorageProvider: UserStorageProvider,
    private val clientConfig: ClientConfig,
    private val platformUserStorageProperties: PlatformUserStorageProperties,
    networkStateObserver: NetworkStateObserver,
    private val logoutCallback: LogoutCallback,
) : CoroutineScope {
    private val userStorage = userStorageProvider.getOrCreate(
        userId,
        platformUserStorageProperties,
        kaliumConfigs.shouldEncryptData(),
        kaliumConfigs.dbInvalidationControlEnabled
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
        KaliumLogger.UserClientData(userId.toLogString(), _clientId?.toLogString() ?: "")
    }

    private val cachedClientIdClearer: CachedClientIdClearer = object : CachedClientIdClearer {
        override fun invoke() {
            _clientId = null
        }
    }

    private suspend fun waitUntilClientIdIsAvailable() {
        if (_clientId == null) {
            clientRepository.observeCurrentClientId().filterNotNull().first()
        }
    }

    internal val callMapper: CallMapper get() = MapperProvider.callMapper(userId)

    public val qualifiedIdMapper: QualifiedIdMapper get() = MapperProvider.qualifiedIdMapper(userId)

    public val federatedIdMapper: FederatedIdMapper
        get() = MapperProvider.federatedIdMapper(
            userId,
            qualifiedIdMapper,
            globalScope.sessionRepository
        )

    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider
        get() = IsClientAsyncNotificationsCapableProviderImpl(clientRegistrationStorage, this)

    internal val clientIdProvider = CurrentClientIdProvider { clientId() }
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

    private val invalidateTeamId = {
        _teamId = Either.Left(CoreFailure.Unknown(Throwable("NotInitialized")))
    }

    private val selfTeamId = SelfTeamIdProvider { teamId() }

    private val epochChangesObserver: EpochChangesObserver = EpochChangesObserverImpl()

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
        currentClientIdProvider = clientIdProvider,
        tokenStorage = globalPreferences.authTokenStorage,
        logout = { logoutReason -> logout(reason = logoutReason, waitUntilCompletes = true) }
    )
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(
        sessionManager = sessionManager,
        selfUserId = UserIdDTO(userId.value, userId.domain),
        userAgent = userAgent,
        certificatePinning = kaliumConfigs.certPinningConfig,
        mockEngine = kaliumConfigs.mockedRequests?.let { MockUnboundNetworkClient.createMockEngine(it) },
        mockWebSocketSession = kaliumConfigs.mockedWebSocket?.session,
        kaliumLogger = userScopedLogger
    )
    private val featureSupport: FeatureSupport = FeatureSupportImpl(
        sessionManager.serverConfig().metaData.commonApiVersion.version
    )

    internal val cellsClient: HttpClient
        get() = authenticatedNetworkContainer.cellsHttpClient

    public val authenticationScope: AuthenticationScope by lazy {
        authenticationScopeProvider.provide(
            serverConfig = sessionManager.getServerConfig(),
            proxyCredentials = sessionManager.getProxyCredentials(),
            globalDatabase = globalDatabaseBuilder,
            kaliumConfigs = kaliumConfigs,
        )
    }

    internal val userConfigRepository: UserConfigRepository
        get() = UserConfigDataSource(
            userStorage.database.userPrefsDAO,
            userStorage.database.userConfigDAO,
            kaliumConfigs
        )

    private val userPropertyRepository: UserPropertyRepository
        get() = UserPropertyDataSource(
            readReceipts = ReadReceiptsPropertyDataSource(authenticatedNetworkContainer.propertiesApi, userConfigRepository),
            typingIndicator = TypingIndicatorPropertyDataSource(authenticatedNetworkContainer.propertiesApi, userConfigRepository),
            screenshotCensoring = ScreenshotCensoringPropertyDataSource(authenticatedNetworkContainer.propertiesApi, userConfigRepository),
            userPropertiesSync = UserPropertiesSyncDataSource(authenticatedNetworkContainer.propertiesApi, userConfigRepository),
            conversationFolders = ConversationFoldersPropertyDataSource(authenticatedNetworkContainer.propertiesApi, userId),
        )

    private val keyPackageLimitsProvider: KeyPackageLimitsProvider
        get() = KeyPackageLimitsProviderImpl(kaliumConfigs)

    private val proteusMigrationRecoveryHandler: ProteusMigrationRecoveryHandler by lazy {
        ProteusMigrationRecoveryHandlerImpl(lazy { logout })
    }

    internal val proteusClientProvider: ProteusClientProvider by lazy {
        ProteusClientProviderImpl(
            rootProteusPath = rootPathsProvider.rootProteusPath(userId),
            userId = userId,
            passphraseStorage = globalPreferences.passphraseStorage,
            proteusMigrationRecoveryHandler = proteusMigrationRecoveryHandler
        )
    }

    private val localEventRepository: LocalEventRepository = LocalEventRepositoryImpl()

    private val mlsTransportProvider: MLSTransportProvider by lazy {
        MLSTransportProviderImpl(
            selfUserId = userId,
            mlsMessageApi = authenticatedNetworkContainer.mlsMessageApi,
            localEventRepository = localEventRepository
        )
    }

    private val mlsClientProvider: MLSClientProvider by lazy {
        MLSClientProviderImpl(
            rootKeyStorePath = rootPathsProvider.rootMLSPath(userId),
            userId = userId,
            currentClientIdProvider = clientIdProvider,
            passphraseStorage = globalPreferences.passphraseStorage,
            userConfigRepository = userConfigRepository,
            featureConfigRepository = featureConfigRepository,
            mlsTransportProvider = mlsTransportProvider,
            epochObserver = epochChangesObserver,
            processingScope = this@UserSessionScope
        )
    }

    private val checkRevocationList: RevocationListChecker
        get() = RevocationListCheckerImpl(
            certificateRevocationListRepository = certificateRevocationListRepository,
            featureSupport = featureSupport,
            userConfigRepository = userConfigRepository
        )

    private val mlsMutex: Mutex = Mutex()

    private val mlsConversationRepository: MLSConversationRepository
        get() = MLSConversationDataSource(
            userId,
            keyPackageRepository,
            userStorage.database.conversationDAO,
            authenticatedNetworkContainer.clientApi,
            mlsPublicKeysRepository,
            proposalTimersFlow,
            keyPackageLimitsProvider,
            checkRevocationList,
            certificateRevocationListRepository,
            mutex = mlsMutex
        )

    private val mlsMissingUsersRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler = {
        MLSMissingUsersMessageRejectionHandlerImpl(
            mlsConversationRepository,
            ConversationProtocolGetterImpl(userStorage.database.conversationDAO),
            userScopedLogger
        )
    }

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

    private val notificationTokenRepository get() = NotificationTokenDataSource(globalPreferences.tokenStorage)

    private val subconversationRepository =
        SubconversationRepositoryImpl(conversationApi = authenticatedNetworkContainer.conversationApi)

    private val conversationRepository: ConversationRepository
        get() = ConversationDataSource(
            userId,
            userStorage.database.conversationDAO,
            userStorage.database.memberDAO,
            authenticatedNetworkContainer.conversationApi,
            userStorage.database.messageDAO,
            userStorage.database.messageDraftDAO,
            userStorage.database.clientDAO,
            authenticatedNetworkContainer.clientApi,
            userStorage.database.conversationMetaDataDAO,
            userStorage.database.metadataDAO,
        )

    private val conversationMetaDataRepository: ConversationMetaDataRepository
        get() = ConversationMetaDataDataSource(
            userStorage.database.conversationMetaDataDAO,
        )

    private val conversationFolderRepository: ConversationFolderRepository
        get() = ConversationFolderDataSource(
            userStorage.database.conversationFolderDAO,
            authenticatedNetworkContainer.propertiesApi,
            userId
        )

    private val conversationGroupRepository: ConversationGroupRepository
        get() = ConversationGroupRepositoryImpl(
            mlsConversationRepository,
            joinExistingMLSConversationUseCase,
            localEventRepository,
            conversationMessageTimerEventHandler,
            userStorage.database.conversationDAO,
            authenticatedNetworkContainer.conversationApi,
            newConversationMembersRepository,
            userRepository,
            lazy { newGroupConversationSystemMessagesCreator },
            userId,
            selfTeamId,
            legalHoldHandler,
            cryptoTransactionProvider
        )

    private val newConversationMembersRepository: NewConversationMembersRepository
        get() = NewConversationMembersRepositoryImpl(
            userStorage.database.memberDAO,
            lazy { newGroupConversationSystemMessagesCreator }
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

    private val messageDraftRepository: MessageDraftRepository
        get() = MessageDraftDataSource(
            messageDraftDAO = userStorage.database.messageDraftDAO,
        )

    private val slowSyncRepository: SlowSyncRepository by lazy {
        SlowSyncRepositoryImpl(userStorage.database.metadataDAO, userScopedLogger)
    }
    private val incrementalSyncRepository: IncrementalSyncRepository by lazy {
        InMemoryIncrementalSyncRepository(userScopedLogger)
    }

    private val legalHoldSystemMessagesHandler = LegalHoldSystemMessagesHandlerImpl(
        selfUserId = userId,
        persistMessage = persistMessage,
        conversationRepository = conversationRepository,
        messageRepository = messageRepository
    )

    private val legalHoldHandler by lazy {
        LegalHoldHandlerImpl(
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
    }

    private val userRepository: UserRepository
        get() = UserDataSource(
            userDAO = userStorage.database.userDAO,
            clientDAO = userStorage.database.clientDAO,
            memberDAO = userStorage.database.memberDAO,
            selfApi = authenticatedNetworkContainer.selfApi,
            userDetailsApi = authenticatedNetworkContainer.userDetailsApi,
            upgradePersonalToTeamApi = authenticatedNetworkContainer.upgradePersonalToTeamApi,
            teamsApi = authenticatedNetworkContainer.teamsApi,
            sessionRepository = globalScope.sessionRepository,
            selfUserId = userId,
            selfTeamIdProvider = selfTeamId,
            legalHoldHandler = legalHoldHandler
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

    private val persistConversationsUseCase: PersistConversationsUseCase
        get() = PersistConversationsUseCaseImpl(
            selfUserId = userId,
            conversationRepository = conversationRepository,
            selfTeamIdProvider = selfTeamId
        )

    private val persistConversationUseCase: PersistConversationUseCase
        get() = PersistConversationUseCaseImpl(
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase,
        )

    private val fetchMLSOneToOneConversationUseCase: FetchMLSOneToOneConversationUseCase
        get() = FetchMLSOneToOneConversationUseCaseImpl(
            selfUserId = userId,
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase
        )

    public val fetchConversationUseCase: FetchConversationUseCase
        get() = FetchConversationUseCaseImpl(
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase,
            transactionProvider = cryptoTransactionProvider,
        )

    private val fetchConversationIfUnknownUseCase: FetchConversationIfUnknownUseCase
        get() = FetchConversationIfUnknownUseCaseImpl(
            conversationRepository = conversationRepository,
            fetchConversation = fetchConversationUseCase
        )

    private val fetchConversationsUseCase: FetchConversationsUseCase
        get() = FetchConversationsUseCaseImpl(
            conversationRepository = conversationRepository,
            persistConversations = persistConversationsUseCase
        )

    private val connectionRepository: ConnectionRepository
        get() = ConnectionDataSource(
            userStorage.database.conversationDAO,
            userStorage.database.memberDAO,
            userStorage.database.connectionDAO,
            authenticatedNetworkContainer.connectionApi,
            userStorage.database.userDAO,
            conversationRepository,
            persistConversationsUseCase
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

    public val backup: BackupScope
        get() = BackupScope(
            userId = userId,
            clientIdProvider = clientIdProvider,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
            userStorage = userStorage,
            globalPreferences = globalPreferences,
        )

    public val multiPlatformBackup: MultiPlatformBackupScope
        get() = MultiPlatformBackupScope(
            selfUserId = userId,
            backupRepository = backupRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    internal val persistMessage: PersistMessageUseCase
        get() = PersistMessageUseCaseImpl(messageRepository, userId, NotificationEventsManagerImpl, persistMessageCallbackManager)

    private val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase
        get() = AddSystemMessageToAllConversationsUseCaseImpl(messageRepository, userId)

    private val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
        get() = RestartSlowSyncProcessForRecoveryUseCaseImpl(slowSyncRepository)

    private val callRepository: CallRepository by lazy {
        CallDataSource(
            callApi = authenticatedNetworkContainer.callApi,
            serverTimeApi = authenticatedNetworkContainer.serverTimeApi,
            qualifiedIdMapper = qualifiedIdMapper,
            callDAO = userStorage.database.callDAO,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            subconversationRepository = subconversationRepository,
            joinSubconversation = joinSubconversationUseCase,
            leaveSubconversation = leaveSubconversationUseCase,
            userRepository = userRepository,
            epochChangesObserver = epochChangesObserver,
            teamRepository = teamRepository,
            persistMessage = persistMessage,
            callMapper = callMapper,
            federatedIdMapper = federatedIdMapper,
            transactionProvider = cryptoTransactionProvider
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

    private val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler
        get() = userSessionWorkScheduler

    private val assetRepository: AssetRepository
        get() = AssetDataSource(
            assetApi = authenticatedNetworkContainer.assetApi,
            assetDao = userStorage.database.assetDAO,
            assetAuditLog = lazy { users.assetAuditLog },
            kaliumFileSystem = kaliumFileSystem
        )

    private val eventGatherer: EventGatherer
        get() = EventGathererImpl(
            isClientAsyncNotificationsCapableProvider = isClientAsyncNotificationsCapableProvider,
            eventRepository = eventRepository,
            logger = userScopedLogger
        )

    internal val cryptoTransactionProvider: CryptoTransactionProvider
        get() = CryptoTransactionProviderImpl(
            mlsClientProvider = mlsClientProvider,
            proteusClientProvider = proteusClientProvider,
        )

    private val eventProcessor: EventProcessor by lazy {
        EventProcessorImpl(
            conversationEventReceiver = conversationEventReceiver,
            userEventReceiver = userEventReceiver,
            teamEventReceiver = teamEventReceiver,
            featureConfigEventReceiver = featureConfigEventReceiver,
            userPropertiesEventReceiver = userPropertiesEventReceiver,
            federationEventReceiver = federationEventReceiver,
            processingScope = this@UserSessionScope,
            logger = userScopedLogger,
        )
    }

    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider
        get() = SlowSlowSyncCriteriaProviderImpl(clientRepository, logoutRepository)

    @Deprecated("Use syncStateObserver instead", ReplaceWith("syncStateObserver"))
    public val syncManager: SyncManager
        get() = syncStateObserver.value

    internal val syncStateObserver: Lazy<SyncStateObserver> = lazy {
        SyncStateObserverImpl(
            slowSyncRepository = slowSyncRepository,
            incrementalSyncRepository = incrementalSyncRepository,
            syncScope = this,
            logger = userScopedLogger
        )
    }

    public val syncExecutor: SyncExecutor by lazy {
        SyncExecutorImpl(
            syncStateObserver.value,
            slowSyncManager,
            incrementalSyncManager,
            this,
            userScopedLogger = userScopedLogger
        )
    }

    private val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCaseImpl(
            conversationRepository,
            systemMessageInserter,
            fetchConversationsUseCase,
            cryptoTransactionProvider
        )

    private val updateConversationProtocolUseCase: UpdateConversationProtocolUseCase
        get() = UpdateConversationProtocolUseCaseImpl(
            conversationRepository,
            persistConversationsUseCase
        )

    private val syncConnections: SyncConnectionsUseCase
        get() = SyncConnectionsUseCaseImpl(
            connectionRepository = connectionRepository,
            transactionProvider = cryptoTransactionProvider
        )

    private val syncSelfUser: SyncSelfUserUseCase get() = SyncSelfUserUseCaseImpl(userRepository)
    private val syncUserProperties: SyncUserPropertiesUseCase get() = SyncUserPropertiesUseCaseImpl(userPropertyRepository)
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
            mlsConversationRepository,
            fetchMLSOneToOneConversationUseCase,
            fetchConversationUseCase,
            resetMlsConversation,
            userId,
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
            joinExistingMLSConversationUseCase,
            cryptoTransactionProvider
        )

    private val joinSubconversationUseCase: JoinSubconversationUseCase
        get() = JoinSubconversationUseCaseImpl(
            authenticatedNetworkContainer.conversationApi,
            mlsConversationRepository,
            subconversationRepository,
            cryptoTransactionProvider
        )

    private val leaveSubconversationUseCase: LeaveSubconversationUseCase
        get() = LeaveSubconversationUseCaseImpl(
            authenticatedNetworkContainer.conversationApi,
            subconversationRepository,
            userId,
            clientIdProvider,
        )

    private val mlsOneOnOneConversationResolver: MLSOneOnOneConversationResolver
        get() = MLSOneOnOneConversationResolverImpl(
            conversationRepository,
            joinExistingMLSConversationUseCase,
            fetchMLSOneToOneConversationUseCase
        )

    private val oneOnOneMigrator: OneOnOneMigrator
        get() = OneOnOneMigratorImpl(
            mlsOneOnOneConversationResolver,
            conversationGroupRepository,
            conversationRepository,
            messageRepository,
            userRepository,
            systemMessageInserter
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
            isClientAsyncNotificationsCapableProvider,
            eventRepository,
            syncSelfUser,
            syncUserProperties,
            syncFeatureConfigsUseCase,
            updateSupportedProtocols,
            syncConversations,
            syncConnections,
            syncSelfTeamUseCase,
            syncContacts,
            joinExistingMLSConversations,
            fetchLegalHoldForSelfUserFromRemoteUseCase,
            oneOnOneResolver,
            cryptoTransactionProvider
        )
    }

    private val slowSyncRecoveryHandler: SlowSyncRecoveryHandler
        get() = SlowSyncRecoveryHandlerImpl(logout)

    private val syncMigrationStepsProvider: () -> SyncMigrationStepsProvider = {
        SyncMigrationStepsProviderImpl(
            accountRepository = lazy { accountRepository },
            selfTeamIdProvider = selfTeamId,
            oldUserConfigStorage = lazy {
                UserConfigStorageFactory().create(userId, kaliumConfigs.shouldEncryptData(), platformUserStorageProperties)
            },
            newUserConfigStorage = lazy { userStorage.database.userPrefsDAO }
        )
    }

    private val slowSyncManager: SlowSyncManager by lazy {
        SlowSyncManager(
            slowSyncCriteriaProvider,
            slowSyncRepository,
            slowSyncWorker,
            slowSyncRecoveryHandler,
            networkStateObserver,
            syncMigrationStepsProvider,
            userScopedLogger,
        )
    }
    private val mlsConversationsRecoveryManager: MLSConversationsRecoveryManager by lazy {
        MLSConversationsRecoveryManagerImpl(
            featureSupport,
            incrementalSyncRepository,
            clientRepository,
            recoverMLSConversationsUseCase,
            slowSyncRepository,
            cryptoTransactionProvider,
            userScopedLogger
        )
    }

    private val mlsFaultyKeysConversationsRepairUseCase: MLSFaultyKeysConversationsRepairUseCaseImpl by lazy {
        MLSFaultyKeysConversationsRepairUseCaseImpl(
            selfUserId = userId,
            syncStateObserver = syncStateObserver.value,
            kaliumConfigs = kaliumConfigs,
            userConfigRepository = userConfigRepository,
            repairFaultyRemovalKeys = debug.repairFaultyRemovalKeysUseCase,
            kaliumLogger = userScopedLogger
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
            eventProcessor,
            cryptoTransactionProvider,
            userStorage.database,
            eventRepository,
            userScopedLogger,
        )
    }
    private val incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandlerImpl
        get() = IncrementalSyncRecoveryHandlerImpl(
            restartSlowSyncProcessForRecoveryUseCase,
            eventRepository,
            userScopedLogger,
        )

    private val incrementalSyncManager by lazy {
        IncrementalSyncManager(
            incrementalSyncWorker,
            incrementalSyncRepository,
            incrementalSyncRecoveryHandler,
            networkStateObserver,
            userScopedLogger,
            userSessionWorkScheduler,
        )
    }

    private val localEventManager by lazy {
        LocalEventManagerImpl(
            localEventRepository,
            eventProcessor,
            cryptoTransactionProvider,
            this
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

    private val eventRepository: EventRepository = EventDataSource(
        notificationApi = authenticatedNetworkContainer.notificationApi,
        metadataDAO = userStorage.database.metadataDAO,
        eventDAO = userStorage.database.eventDAO,
        currentClientId = clientIdProvider,
        clientRegistrationStorage = clientRegistrationStorage,
        restartSlowSyncProcessForRecovery = restartSlowSyncProcessForRecoveryUseCase,
        selfUserId = userId,
        logger = userScopedLogger
    )

    private val mlsMigrator: MLSMigrator
        get() = MLSMigratorImpl(
            userId,
            selfTeamId,
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            systemMessageInserter,
            callRepository,
            updateConversationProtocolUseCase,
            cryptoTransactionProvider
        )

    internal val keyPackageManager: KeyPackageManager = KeyPackageManagerImpl(
        featureSupport,
        incrementalSyncRepository,
        lazy { clientRepository },
        lazy { client.refillKeyPackages },
        lazy { client.mlsKeyPackageCountUseCase },
        lazy { users.timestampKeyRepository },
        cryptoTransactionProvider
    )

    internal val keyingMaterialsManager: KeyingMaterialsManager
        get() = KeyingMaterialsManagerImpl(
            featureSupport,
            syncStateObserver.value,
            lazy { clientRepository },
            lazy { conversations.updateMLSGroupsKeyingMaterials },
            lazy { users.timestampKeyRepository },
            this,
        )

    internal val mlsClientManager: MLSClientManager
        get() = MLSClientManagerImpl(
            clientIdProvider,
            isAllowedToRegisterMLSClient,
            syncStateObserver.value,
            lazy { slowSyncRepository },
            lazy { clientRepository },
            lazy {
                RegisterMLSClientUseCaseImpl(
                    mlsClientProvider,
                    clientRepository,
                    keyPackageRepository,
                    keyPackageLimitsProvider,
                    userConfigRepository
                )
            },
            this,
        )

    private val mlsMigrationWorker
        get() = MLSMigrationWorkerImpl(
            userConfigRepository,
            featureConfigRepository,
            mlsConfigHandler,
            mlsMigrationConfigHandler,
            mlsMigrator,
        )

    internal val mlsMigrationManager: MLSMigrationManager
        get() = MLSMigrationManagerImpl(
            kaliumConfigs,
            isMLSEnabled,
            syncStateObserver.value,
            lazy { clientRepository },
            lazy { users.timestampKeyRepository },
            lazy { mlsMigrationWorker },
            this,
        )

    private val mlsPublicKeysRepository: MLSPublicKeysRepository
        get() = MLSPublicKeysRepositoryImpl(
            authenticatedNetworkContainer.mlsPublicKeyApi,
        )

    private val videoStateChecker: VideoStateChecker get() = VideoStateCheckerImpl()

    private val pendingProposalScheduler: PendingProposalScheduler =
        PendingProposalSchedulerImpl(
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            lazy { subconversationRepository },
            cryptoTransactionProvider
        )

    private val callManager: Lazy<CallManager> = lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            currentClientIdProvider = clientIdProvider,
            conversationRepository = conversationRepository,
            userConfigRepository = userConfigRepository,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = messages.messageSender,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            getCallConversationType = getCallConversationType,
            networkStateObserver = networkStateObserver,
            kaliumConfigs = kaliumConfigs,
            createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
        )
    }

    internal val callBackgroundManager: CallBackgroundManager = CallBackgroundManagerImpl(
        callManager = callManager,
        syncStateObserver = syncStateObserver,
        selfUserId = userId,
        logger = userScopedLogger
    )

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

    private val getCallConversationType: GetCallConversationTypeProvider by lazy {
        GetCallConversationTypeProviderImpl(
            userConfigRepository = userConfigRepository,
            conversationMetaDataRepository = conversationMetaDataRepository,
        )
    }

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
            selfUserId = userId
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

    private val dataTransferEventHandler: DataTransferEventHandler
        get() = DataTransferEventHandlerImpl(
            userId,
            userConfigRepository,
            userScopedLogger
        )

    private val inCallReactionsRepository: InCallReactionsRepository by lazy {
        InCallReactionsDataSource()
    }

    private val buttonActionHandler: ButtonActionHandler by lazy {
        ButtonActionHandlerImpl(userId, compositeMessageRepository, userScopedLogger)
    }

    private val applicationMessageHandler: ApplicationMessageHandler
        get() = ApplicationMessageHandlerImpl(
            userRepository,
            messageRepository,
            assetMessageHandler,
            callManager,
            persistMessage,
            persistReaction,
            MessageTextEditHandlerImpl(messageRepository, NotificationEventsManagerImpl),
            MessageMultipartEditHandlerImpl(messageRepository, NotificationEventsManagerImpl),
            LastReadContentHandlerImpl(
                conversationRepository,
                userId,
                isMessageSentInSelfConversation,
                NotificationEventsManagerImpl
            ),
            ClearConversationContentHandlerImpl(
                conversationRepository,
                userId,
                isMessageSentInSelfConversation,
                conversations.clearConversationAssetsLocally,
                deleteConversationUseCase
            ),
            DeleteForMeHandlerImpl(messageRepository, isMessageSentInSelfConversation),
            DeleteMessageHandlerImpl(messageRepository, assetRepository, NotificationEventsManagerImpl, userId),
            messageEncoder,
            receiptMessageHandler,
            buttonActionConfirmationHandler,
            dataTransferEventHandler,
            inCallReactionsRepository,
            buttonActionHandler,
            MessageCompositeEditHandlerImpl(messageRepository),
            userId
        )

    private val staleEpochVerifier: StaleEpochVerifier
        get() = StaleEpochVerifierImpl(
            systemMessageInserter = systemMessageInserter,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            joinExistingMLSConversation = joinExistingMLSConversationUseCase,
            subconversationRepository = subconversationRepository,
            fetchConversation = fetchConversationUseCase
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
            { conversationId, messageId ->
                messages.confirmationDeliveryHandler.enqueueConfirmationDelivery(conversationId, messageId)
            },
            userId,
            staleEpochVerifier,
            resetMlsConversation,
        )

    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
        get() = NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage = persistMessage,
            selfTeamIdProvider = selfTeamId,
            qualifiedIdMapper = qualifiedIdMapper,
            selfUserId = userId
        )

    private val newConversationHandler: NewConversationEventHandler
        get() = NewConversationEventHandlerImpl(
            conversationRepository,
            userRepository,
            selfTeamId,
            newGroupConversationSystemMessagesCreator,
            oneOnOneResolver,
            persistConversationUseCase
        )
    private val deletedConversationHandler: DeletedConversationEventHandler
        get() = DeletedConversationEventHandlerImpl(
            userRepository,
            conversationRepository,
            NotificationEventsManagerImpl,
            deleteConversationUseCase
        )
    private val memberJoinHandler: MemberJoinEventHandler
        get() = MemberJoinEventHandlerImpl(
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            persistMessage = persistMessage,
            legalHoldHandler = legalHoldHandler,
            newGroupConversationSystemMessagesCreator = newGroupConversationSystemMessagesCreator,
            selfUserId = userId,
            fetchConversationUseCase
        )
    private val memberLeaveHandler: MemberLeaveEventHandler
        get() = MemberLeaveEventHandlerImpl(
            memberDAO = userStorage.database.memberDAO,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            persistMessage = persistMessage,
            updateConversationClientsForCurrentCall = updateConversationClientsForCurrentCall,
            legalHoldHandler = legalHoldHandler,
            selfTeamIdProvider = selfTeamId,
            deleteConversation = deleteConversationUseCase,
            selfUserId = userId
        )
    private val memberChangeHandler: MemberChangeEventHandler
        get() = MemberChangeEventHandlerImpl(
            conversationRepository,
            fetchConversationIfUnknownUseCase

        )
    private val mlsWelcomeHandler: MLSWelcomeEventHandler
        get() = MLSWelcomeEventHandlerImpl(
            conversationRepository = conversationRepository,
            oneOnOneResolver = oneOnOneResolver,
            refillKeyPackages = client.refillKeyPackages,
            revocationListChecker = checkRevocationList,
            certificateRevocationListRepository = certificateRevocationListRepository,
            joinExistingMLSConversation = joinExistingMLSConversationUseCase,
            fetchConversationIfUnknown = fetchConversationIfUnknownUseCase
        )

    private val renamedConversationHandler: RenamedConversationEventHandler
        get() = RenamedConversationEventHandlerImpl(
            userStorage.database.conversationDAO,
            persistMessage
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
            systemMessageInserter = systemMessageInserter,
            callRepository = callRepository,
            updateConversationProtocolUseCase
        )

    private val channelAddPermissionUpdateEventHandler: ChannelAddPermissionUpdateEventHandler
        get() = ChannelAddPermissionUpdateEventHandlerImpl(
            conversationRepository = conversationRepository
        )

    private val conversationAccessUpdateEventHandler: AccessUpdateEventHandler
        get() = AccessUpdateEventHandler(
            conversationDAO = userStorage.database.conversationDAO,
            selfUserId = userId,
            systemMessageInserter = systemMessageInserter
        )

    private val mlsResetConversationEventHandler: MLSResetConversationEventHandler
        get() = MLSResetConversationEventHandlerImpl(
            mlsConversationRepository = mlsConversationRepository,
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
            protocolUpdateEventHandler,
            channelAddPermissionUpdateEventHandler,
            conversationAccessUpdateEventHandler,
            mlsResetConversationEventHandler,
        )
    }
    override val coroutineContext: CoroutineContext = SupervisorJob()
    private val persistMessageCallbackManager = PersistMessageCallbackManagerImpl(this)

    public fun registerMessageCallback(callback: PersistMessageCallback) {
        persistMessageCallbackManager.register(callback)
    }

    public fun unregisterMessageCallback(callback: PersistMessageCallback) {
        persistMessageCallbackManager.unregister(callback)
    }

    private val legalHoldRequestHandler = LegalHoldRequestHandlerImpl(
        selfUserId = userId,
        userConfigRepository = userConfigRepository
    )

    public val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase
        get() = ObserveLegalHoldStateForUserUseCaseImpl(clientRepository)

    public val observeAnalyticsTrackingIdentifierStatus: ObserveAnalyticsTrackingIdentifierStatusUseCase
        get() = ObserveAnalyticsTrackingIdentifierStatusUseCase(userConfigRepository, userScopedLogger)

    public val setNewUserTrackingIdentifier: SetNewUserTrackingIdentifierUseCase
        get() = SetNewUserTrackingIdentifierUseCase(userConfigRepository)

    public val getCurrentAnalyticsTrackingIdentifier: GetCurrentAnalyticsTrackingIdentifierUseCase
        get() = GetCurrentAnalyticsTrackingIdentifierUseCase(userConfigRepository)

    public val analyticsIdentifierManager: AnalyticsIdentifierManager
        get() = AnalyticsIdentifierManager(
            messages.messageSender,
            userConfigRepository,
            userId,
            clientIdProvider,
            selfConversationIdProvider,
            syncManager,
            userScopedLogger
        )

    public suspend fun observeIfE2EIRequiredDuringLogin(): Flow<Boolean?> = clientRepository.observeIsClientRegistrationBlockedByE2EI()

    public val observeLegalHoldForSelfUser: ObserveLegalHoldStateForSelfUserUseCase
        get() = ObserveLegalHoldStateForSelfUserUseCaseImpl(userId, observeLegalHoldStateForUser, observeLegalHoldRequest)

    public val observeLegalHoldChangeNotifiedForSelf: ObserveLegalHoldChangeNotifiedForSelfUseCase
        get() = ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl(userId, userConfigRepository, observeLegalHoldStateForUser)

    public val markLegalHoldChangeAsNotifiedForSelf: MarkLegalHoldChangeAsNotifiedForSelfUseCase
        get() = MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl(userConfigRepository)

    public val observeLegalHoldRequest: ObserveLegalHoldRequestUseCase
        get() = ObserveLegalHoldRequestUseCaseImpl(
            userConfigRepository = userConfigRepository,
            transactionProvider = cryptoTransactionProvider
        )

    public val approveLegalHoldRequest: ApproveLegalHoldRequestUseCase
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

    internal val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase
        get() = MembersHavingLegalHoldClientUseCaseImpl(clientRepository)

    private val updateSelfClientCapabilityToConsumableNotifications by lazy {
        UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl(
            selfClientIdProvider = clientIdProvider,
            clientRepository = clientRepository,
            clientRemoteRepository = clientRemoteRepository,
            incrementalSyncRepository = incrementalSyncRepository,
            selfServerConfig = users.serverLinks,
            syncRequester = { syncExecutor.request { waitUntilLiveOrFailure() } },
            slowSyncRepository = slowSyncRepository,
            logger = userScopedLogger
        )
    }

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
            lazy { newGroupConversationSystemMessagesCreator },
            legalHoldRequestHandler,
            legalHoldHandler
        )

    private val userPropertiesEventReceiver: UserPropertiesEventReceiver
        get() = UserPropertiesEventReceiverImpl(userConfigRepository, conversationFolderRepository)

    private val federationEventReceiver: FederationEventReceiver
        get() = FederationEventReceiverImpl(
            conversationRepository,
            connectionRepository,
            userRepository,
            userStorage.database.memberDAO,
            persistMessage,
            userId
        )

    private val teamEventReceiver: TeamEventReceiver
        get() = TeamEventReceiverImpl(userRepository, persistMessage, userId)

    private val guestRoomConfigHandler
        get() = GuestRoomConfigHandler(userConfigRepository, kaliumConfigs)

    private val fileSharingConfigHandler
        get() = FileSharingConfigHandler(userConfigRepository)

    private val mlsConfigHandler
        get() = MLSConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes, cryptoTransactionProvider)

    private val mlsMigrationConfigHandler
        get() = MLSMigrationConfigHandler(userConfigRepository, updateSupportedProtocolsAndResolveOneOnOnes, cryptoTransactionProvider)

    private val classifiedDomainsConfigHandler
        get() = ClassifiedDomainsConfigHandler(userConfigRepository)

    private val conferenceCallingConfigHandler
        get() = ConferenceCallingConfigHandler(userConfigRepository)

    private val consumableNotificationsConfigHandler
        get() = ConsumableNotificationsConfigHandler(userConfigRepository)

    private val appsFeatureHandler
        get() = AppsFeatureHandler(userConfigRepository)

    private val secondFactorPasswordChallengeConfigHandler
        get() = SecondFactorPasswordChallengeConfigHandler(userConfigRepository)

    private val selfDeletingMessagesConfigHandler
        get() = SelfDeletingMessagesConfigHandler(userConfigRepository, kaliumConfigs)

    private val e2eiConfigHandler
        get() = E2EIConfigHandler(userConfigRepository)

    private val appLockConfigHandler
        get() = AppLockConfigHandler(userConfigRepository)

    private val allowedGlobalOperationsHandler
        get() = AllowedGlobalOperationsHandler(userConfigRepository)

    private val cellsConfigHandler
        get() = CellsConfigHandler(userConfigRepository)

    private val enableUserProfileQRCodeConfigHandler
        get() = EnableUserProfileQRCodeConfigHandler(userConfigRepository)

    private val assetAuditLogConfigHandler
        get() = AssetAuditLogConfigHandler(userConfigRepository)

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
            appLockConfigHandler,
            allowedGlobalOperationsHandler,
            cellsConfigHandler,
            enableUserProfileQRCodeConfigHandler,
            assetAuditLogConfigHandler,
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

    internal val userConfigSyncWorker: UserConfigSyncWorker by lazy {
        UserConfigSyncWorkerImpl(
            incrementalSyncRepository = incrementalSyncRepository,
            syncFeatureConfigsUseCase = syncFeatureConfigsUseCase,
            proteusPreKeyRefiller = proteusPreKeyRefiller,
            mlsPublicKeysRepository = mlsPublicKeysRepository,
            acmeCertificatesSyncUseCase = acmeCertificatesSyncUseCase,
            kaliumLogger = userScopedLogger,
        )
    }

    internal val pendingMessagesSenderWorker: PendingMessagesSenderWorker by lazy {
        PendingMessagesSenderWorker(
            messageRepository = messageRepository,
            messageSender = messages.messageSender,
            userId = userId,
        )
    }

    internal fun buildAudioNormalizedLoudnessWorker(
        conversationId: ConversationId,
        messageId: String
    ): AudioNormalizedLoudnessWorker = AudioNormalizedLoudnessWorkerImpl(
        conversationId = conversationId,
        messageId = messageId,
        messageScope = messages,
        audioNormalizedLoudnessBuilder = globalScope.audioNormalizedLoudnessBuilder
    )

    private val keyPackageRepository: KeyPackageRepository
        get() = KeyPackageDataSource(
            clientIdProvider,
            authenticatedNetworkContainer.keyPackageApi,
            userId
        )

    private val logoutRepository: LogoutRepository = LogoutDataSource(
        authenticatedNetworkContainer.logoutApi,
        userStorage.database.metadataDAO
    )

    private val backupRepository: BackupRepository
        get() = BackupDataSource(
            selfUserId = userId,
            userDAO = userStorage.database.userDAO,
            conversationDAO = userStorage.database.conversationDAO,
            messageDAO = userStorage.database.messageDAO,
            reactionDAO = userStorage.database.reactionDAO,
        )

    public val observeSyncState: ObserveSyncStateUseCase
        get() = ObserveSyncStateUseCaseImpl(syncManager)

    private val avsSyncStateReporter: AvsSyncStateReporter by lazy {
        AvsSyncStateReporterImpl(
            callManager = callManager,
            incrementalSyncRepository = incrementalSyncRepository,
            kaliumLogger = userScopedLogger
        )
    }

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl(selfUserId = userId)

    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector
        get() = OneOnOneProtocolSelectorImpl(
            userRepository,
            userConfigRepository
        )

    private val acmeCertificatesSyncUseCase: ACMECertificatesSyncUseCase by lazy {
        ACMECertificatesSyncUseCaseImpl(
            e2eiRepository = e2eiRepository,
            kaliumLogger = userScopedLogger,
            isE2EIEnabledUseCase = isE2EIEnabled
        )
    }

    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
        get() = RefreshUsersWithoutMetadataUseCaseImpl(
            userRepository
        )

    private val isAllowedToUseAsyncNotifications: IsAllowedToUseAsyncNotificationsUseCase
        get() = IsAllowedToUseAsyncNotificationsUseCaseImpl(
            userConfigRepository = userConfigRepository,
            isAllowedByCurrentBackendVersionProvider = {
                sessionManager.serverConfig().metaData.commonApiVersion.version >= MIN_API_VERSION_FOR_CONSUMABLE_NOTIFICATIONS
            }
        )

    @OptIn(DelicateKaliumApi::class)
    public val client: ClientScope by lazy {
        ClientScope(
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
            syncFeatureConfigsUseCase,
            userConfigRepository,
            cryptoTransactionProvider,
            isAllowedToUseAsyncNotifications
        )
    }
    public val conversations: ConversationScope by lazy {
        ConversationScope(
            conversationRepository,
            conversationGroupRepository,
            connectionRepository,
            userRepository,
            conversationFolderRepository,
            syncManager,
            mlsConversationRepository,
            clientIdProvider,
            messages.messageSender,
            teamRepository,
            slowSyncRepository,
            userId,
            selfConversationIdProvider,
            persistMessage,
            selfTeamId,
            messages.sendConfirmation,
            renamedConversationHandler,
            authenticationScope.serverConfigRepository,
            userStorage,
            userPropertyRepository,
            messages.deleteEphemeralMessageEndDate,
            oneOnOneResolver,
            this,
            userScopedLogger,
            refreshUsersWithoutMetadata,
            sessionManager.getServerConfig().links,
            messages.messageRepository,
            assetRepository,
            newGroupConversationSystemMessagesCreator,
            deleteConversationUseCase,
            persistConversationsUseCase,
            cryptoTransactionProvider,
            resetMlsConversation,
            systemMessageInserter
        )
    }

    public val channels: ChannelsScope by lazy {
        ChannelsScope(
            { users.getSelfUser },
            { conversationRepository },
            { userStorage.database.metadataDAO },
            { userRepository }
        )
    }

    public val debug: DebugScope by lazy {
        DebugScope(
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            { joinExistingMLSConversationUseCase },
            clientRepository,
            clientRemoteRepository,
            clientIdProvider,
            preKeyRepository,
            userRepository,
            featureConfigRepository,
            userId,
            assetRepository,
            eventRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            selfConversationIdProvider,
            staleEpochVerifier,
            eventProcessor,
            legalHoldHandler,
            notificationTokenRepository,
            this,
            userStorage,
            mlsMissingUsersRejectionHandlerProvider,
            updateSelfClientCapabilityToConsumableNotifications,
            users.serverLinks,
            fetchConversationUseCase,
            resetMlsConversation,
            cryptoTransactionProvider,
            client.refillKeyPackages,
            userScopedLogger,
        )
    }

    public val messages: MessageScope by lazy {
        MessageScope(
            connectionRepository,
            messageDraftRepository,
            userId,
            clientIdProvider,
            selfConversationIdProvider,
            messageRepository,
            conversationRepository,
            lazy { cells.messageAttachmentsDraftRepository },
            mlsConversationRepository,
            clientRepository,
            clientRemoteRepository,
            preKeyRepository,
            userRepository,
            assetRepository,
            reactionRepository,
            receiptRepository,
            syncManager,
            slowSyncRepository,
            messageSendingScheduler,
            audioNormalizedLoudnessScheduler,
            userPropertyRepository,
            incrementalSyncRepository,
            protoContentMapper,
            observeSelfDeletingMessages,
            messageMetadataRepository,
            staleEpochVerifier,
            legalHoldHandler,
            observeFileSharingStatus,
            lazy { cells.getMessageAttachmentsUseCase },
            lazy { cells.publishAttachments },
            lazy { cells.removeAttachments },
            lazy { cells.deleteAttachmentsUseCase },
            fetchConversationUseCase,
            cryptoTransactionProvider,
            compositeMessageRepository,
            { joinExistingMLSConversationUseCase },
            globalScope.audioNormalizedLoudnessBuilder,
            mlsMissingUsersRejectionHandlerProvider,
            persistMessageCallbackManager,
            this,
            userScopedLogger
        )
    }

    public val users: UserScope by lazy {
        UserScope(
            userRepository,
            userConfigRepository,
            accountRepository,
            syncManager,
            assetRepository,
            teamRepository,
            globalScope.sessionRepository,
            authenticationScope.serverConfigRepository,
            userId,
            userStorage.database.metadataDAO,
            userPropertyRepository,
            messages.messageSender,
            clientIdProvider,
            e2eiRepository,
            mlsConversationRepository,
            conversationRepository,
            team.isSelfATeamMember,
            updateSupportedProtocols,
            clientRepository,
            joinExistingMLSConversations,
            refreshUsersWithoutMetadata,
            isE2EIEnabled,
            certificateRevocationListRepository,
            incrementalSyncRepository,
            sessionManager,
            selfTeamId,
            checkRevocationList,
            userScopedLogger,
            getTeamUrlUseCase,
            isMLSEnabled,
            globalScope.updateApiVersions,
            userConfigSyncWorker,
            mlsClientManager,
            mlsMigrationManager,
            keyingMaterialsManager,
            cryptoTransactionProvider,
            this,
        )
    }

    public val search: SearchScope by lazy {
        SearchScope(
            mlsPublicKeysRepository = mlsPublicKeysRepository,
            getDefaultProtocol = getDefaultProtocol,
            getConversationProtocolInfo = conversations.getConversationProtocolInfo,
            searchUserRepository = searchUserRepository,
            selfUserId = userId,
            sessionRepository = globalScope.sessionRepository,
            kaliumConfigs = kaliumConfigs
        )
    }

    private val clearUserData: ClearUserDataUseCase get() = ClearUserDataUseCaseImpl(userStorage)

    private val validateAssetMimeType: ValidateAssetFileTypeUseCase get() = ValidateAssetFileTypeUseCaseImpl()

    public val logout: LogoutUseCase
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
    public val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = PersistPersistentWebSocketConnectionStatusUseCaseImpl(userId, globalScope.sessionRepository)

    public val getPersistentWebSocketStatus: GetPersistentWebSocketStatus
        get() = GetPersistentWebSocketStatusImpl(userId, globalScope.sessionRepository)

    private val featureConfigRepository: FeatureConfigRepository
        get() = FeatureConfigDataSource(
            featureConfigApi = authenticatedNetworkContainer.featureConfigApi
        )
    public val isFileSharingEnabled: IsFileSharingEnabledUseCase get() = IsFileSharingEnabledUseCaseImpl(userConfigRepository)
    public val observeFileSharingStatus: ObserveFileSharingStatusUseCase
        get() = ObserveFileSharingStatusUseCaseImpl(userConfigRepository)

    public val observeShouldNotifyForRevokedCertificate: ObserveShouldNotifyForRevokedCertificateUseCase
            by lazy { ObserveShouldNotifyForRevokedCertificateUseCaseImpl(userConfigRepository) }

    public val markNotifyForRevokedCertificateAsNotified: MarkNotifyForRevokedCertificateAsNotifiedUseCase
            by lazy { MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl(userConfigRepository) }

    public val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase
        get() = MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository)

    public val appLockTeamFeatureConfigObserver: AppLockTeamFeatureConfigObserver
        get() = AppLockTeamFeatureConfigObserverImpl(userConfigRepository)

    public val markTeamAppLockStatusAsNotified: MarkTeamAppLockStatusAsNotifiedUseCase
        get() = MarkTeamAppLockStatusAsNotifiedUseCaseImpl(userConfigRepository)

    public val markSelfDeletingMessagesAsNotified: MarkSelfDeletionStatusAsNotifiedUseCase
        get() = MarkSelfDeletionStatusAsNotifiedUseCaseImpl(userConfigRepository)

    public val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
        get() = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository, conversationRepository)

    public val observeTeamSettingsSelfDeletionStatus: ObserveTeamSettingsSelfDeletingStatusUseCase
        get() = ObserveTeamSettingsSelfDeletingStatusUseCaseImpl(userConfigRepository)

    public val persistNewSelfDeletionStatus: PersistNewSelfDeletionTimerUseCase
        get() = PersistNewSelfDeletionTimerUseCaseImpl(conversationRepository)

    public val observeGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase
        get() = ObserveGuestRoomLinkFeatureFlagUseCaseImpl(userConfigRepository)

    public val markFileSharingStatusAsNotified: MarkFileSharingChangeAsNotifiedUseCase
        get() = MarkFileSharingChangeAsNotifiedUseCase(userConfigRepository)

    public val isMLSEnabled: IsMLSEnabledUseCase get() = IsMLSEnabledUseCaseImpl(featureSupport, userConfigRepository)
    public val isE2EIEnabled: IsE2EIEnabledUseCase
        get() = IsE2EIEnabledUseCaseImpl(
            userConfigRepository,
            isMLSEnabled
        )

    public val getDefaultProtocol: GetDefaultProtocolUseCase
        get() = GetDefaultProtocolUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

    public val observeE2EIRequired: ObserveE2EIRequiredUseCase
        get() = ObserveE2EIRequiredUseCaseImpl(
            userConfigRepository,
            featureSupport,
            users.getE2EICertificate,
            clientIdProvider
        )
    public val markE2EIRequiredAsNotified: MarkEnablingE2EIAsNotifiedUseCase
        get() = MarkEnablingE2EIAsNotifiedUseCaseImpl(userConfigRepository)

    @OptIn(DelicateKaliumApi::class)
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
        get() = IsAllowedToRegisterMLSClientUseCaseImpl(
            featureSupport,
            mlsPublicKeysRepository,
            userConfigRepository
        )

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
            appLockConfigHandler,
            channels.channelsFeatureConfigHandler,
            consumableNotificationsConfigHandler,
            allowedGlobalOperationsHandler,
            cellsConfigHandler,
            appsFeatureHandler,
            enableUserProfileQRCodeConfigHandler,
            assetAuditLogConfigHandler,
        )

    public val team: TeamScope
        get() = TeamScope(
            teamRepository = teamRepository,
            slowSyncRepository = slowSyncRepository,
            selfTeamIdProvider = selfTeamId
        )

    public val service: ServiceScope
        get() = ServiceScope(
            serviceRepository,
            teamRepository,
            userConfigRepository,
            selfTeamId
        )

    public val calls: CallsScope
        get() = CallsScope(
            callManager = callManager,
            callBackgroundManager = callBackgroundManager,
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            flowManagerService = flowManagerService,
            mediaManagerService = mediaManagerService,
            syncStateObserver = syncStateObserver,
            qualifiedIdMapper = qualifiedIdMapper,
            currentClientIdProvider = clientIdProvider,
            userConfigRepository = userConfigRepository,
            getCallConversationType = getCallConversationType,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            kaliumConfigs = kaliumConfigs,
            inCallReactionsRepository = inCallReactionsRepository,
            selfUserId = userId,
            userRepository = userRepository
        )

    public val connection: ConnectionScope
        get() = ConnectionScope(
            connectionRepository,
            conversationRepository,
            userRepository,
            oneOnOneResolver,
            newGroupConversationSystemMessagesCreator,
            fetchConversationUseCase,
            cryptoTransactionProvider
        )

    public val observeSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase
        get() = ObserveSecurityClassificationLabelUseCaseImpl(
            conversations.observeConversationMembers,
            conversationRepository,
            userConfigRepository
        )

    public val getOtherUserSecurityClassificationLabel: ObserveOtherUserSecurityClassificationLabelUseCase
        get() = ObserveOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository, userId)

    public val persistScreenshotCensoringConfig: PersistScreenshotCensoringConfigUseCase
        get() = PersistScreenshotCensoringConfigUseCaseImpl(userPropertyRepository = userPropertyRepository)

    public val observeScreenshotCensoringConfig: ObserveScreenshotCensoringConfigUseCase
        get() = ObserveScreenshotCensoringConfigUseCaseImpl(userConfigRepository = userConfigRepository)

    public val fetchConversationMLSVerificationStatus: FetchConversationMLSVerificationStatusUseCase
        get() = FetchConversationMLSVerificationStatusUseCaseImpl(
            conversationRepository,
            fetchMLSVerificationStatusUseCase
        )

    public val kaliumFileSystem: KaliumFileSystem by lazy {
        // Create the cache and asset storage directories
        KaliumFileSystemImpl(dataStoragePaths).also {
            if (!it.exists(dataStoragePaths.cachePath.value.toPath()))
                it.createDirectories(dataStoragePaths.cachePath.value.toPath())
            if (!it.exists(dataStoragePaths.assetStoragePath.value.toPath()))
                it.createDirectories(dataStoragePaths.assetStoragePath.value.toPath())
        }
    }

    public val checkCrlRevocationList: CheckCrlRevocationListUseCase
        get() = CheckCrlRevocationListUseCase(
            certificateRevocationListRepository,
            checkRevocationList,
            cryptoTransactionProvider,
            userScopedLogger
        )

    private val createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
        get() = CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl(
            callRepository = callRepository,
            observeConversationMembers = conversations.observeConversationMembers,
            selfTeamIdProvider = selfTeamId
        )

    public val migrateFromPersonalToTeam: MigrateFromPersonalToTeamUseCase
        get() = MigrateFromPersonalToTeamUseCaseImpl(userId, userRepository, syncContacts, invalidateTeamId)

    internal val getProxyCredentials: GetProxyCredentialsUseCase
        get() = GetProxyCredentialsUseCaseImpl(sessionManager)

    private fun createPushTokenUpdater() = PushTokenUpdater(
        clientRepository,
        notificationTokenRepository,
        pushTokenRepository
    )

    private val fetchMLSVerificationStatusUseCase: FetchMLSVerificationStatusUseCase by lazy {
        FetchMLSVerificationStatusUseCaseImpl(
            mlsClientProvider,
            conversationRepository,
            persistMessage,
            mlsConversationRepository,
            userId,
            userRepository,
            userScopedLogger,
        )
    }

    private val observeE2EIConversationsVerificationStatuses: ObserveE2EIConversationsVerificationStatusesUseCase by lazy {
        ObserveE2EIConversationsVerificationStatusesUseCaseImpl(
            fetchMLSVerificationStatus = fetchMLSVerificationStatusUseCase,
            epochChangesObserver = epochChangesObserver,
            kaliumLogger = userScopedLogger
        )
    }

    private val typingIndicatorSyncManager: TypingIndicatorSyncManager =
        TypingIndicatorSyncManager(
            typingIndicatorIncomingRepository = lazy { conversations.typingIndicatorIncomingRepository },
            observeSyncStateUseCase = observeSyncState,
            kaliumLogger = userScopedLogger,
        )

    private val analyticsRepository: AnalyticsRepository
        get() = AnalyticsDataSource(
            userDAO = userStorage.database.userDAO,
            selfUserId = userId,
            metadataDAO = userStorage.database.metadataDAO
        )

    public val getTeamUrlUseCase: GetTeamUrlUseCase
        get() = GetTeamUrlUseCase(
            userId,
            authenticationScope.serverConfigRepository,
        )

    public val getAnalyticsContactsData: GetAnalyticsContactsDataUseCase
        get() = GetAnalyticsContactsDataUseCase(
            selfTeamIdProvider = selfTeamId,
            analyticsRepository = analyticsRepository,
            userConfigRepository = userConfigRepository,
            coroutineScope = this,
        )

    public val cells: CellsScope by lazy {
        CellsScope(
            cellsClient = cellsClient,
            dao = with(userStorage.database) {
                CellsScope.CellScopeDao(
                    attachmentDraftDao = messageAttachmentDraftDao,
                    conversationsDao = conversationDAO,
                    attachmentsDao = messageAttachments,
                    assetsDao = assetDAO,
                    userDao = userDAO,
                    publicLinkDao = publicLinks,
                    userConfigDAO = userConfigDAO,
                )
            },
            sessionManager = sessionManager,
            accessTokenApi = authenticatedNetworkContainer.accessTokenApi,
        )
    }

    private val deleteConversationUseCase: DeleteConversationUseCase
        get() = DeleteConversationUseCaseImpl(
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
        )

    internal val userSessionWorkScheduler: UserSessionWorkScheduler = globalScope.workSchedulerProvider.userSessionWorkScheduler(this)

    public val resetMlsConversation: ResetMLSConversationUseCase
        get() = ResetMLSConversationUseCaseImpl(
            selfUserId = userId,
            userConfig = userConfigRepository,
            transactionProvider = cryptoTransactionProvider,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            fetchConversationUseCase = fetchConversationUseCase,
            kaliumConfigs = kaliumConfigs,
        )

    public val longWork: LongWorkScope = LongWorkScope(
        { this },
        { slowSyncRepository.slowSyncStatus.map { it is SlowSyncStatus.Ongoing } }
    )

    /**
     * This will start subscribers of observable work per user session, as long as the user is logged in.
     * When the user logs out, this work will be canceled.
     */
    init {
        launch {
            apiMigrationManager.performMigrations()
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
            observeE2EIConversationsVerificationStatuses.invoke()
        }

        launch {
            typingIndicatorSyncManager.execute()
        }

        launch {
            if (isAllowedToUseAsyncNotifications()) {
                updateSelfClientCapabilityToConsumableNotifications()
            }
        }

        launch {
            mlsFaultyKeysConversationsRepairUseCase.invoke()
        }

        launch {
            waitUntilClientIdIsAvailable()
            avsSyncStateReporter.execute()
        }

        launch {
            messages.confirmationDeliveryHandler.sendPendingConfirmations()
        }

        syncExecutor.startAndStopSyncAsNeeded()

        launch {
            localEventManager.startProcessing()
        }

        userSessionWorkScheduler.schedulePeriodicUserConfigSync()

        launch {
            waitUntilClientIdIsAvailable()
            callBackgroundManager.startProcessing()
        }
    }
}

@Mockable
internal fun interface CachedClientIdClearer {
    operator fun invoke()
}
