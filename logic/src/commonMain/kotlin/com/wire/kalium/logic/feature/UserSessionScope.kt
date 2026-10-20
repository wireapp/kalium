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
@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.feature

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
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
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.MLSClientProviderImpl
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProviderImpl
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.guardedBy
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.EpochChangesObserverImpl
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewConversationMembersRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.notification.NotificationEventsManagerImpl
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.SelfUserObservationProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.KaliumConsumer
import com.wire.kalium.logic.di.KaliumConsumerRegistry
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserGraphDependencies
import com.wire.kalium.logic.di.UserSessionGlobalInputs
import com.wire.kalium.logic.di.UserSessionOwnershipInputs
import com.wire.kalium.logic.di.UserSessionResourceInputs
import com.wire.kalium.logic.di.UserSessionRuntime
import com.wire.kalium.logic.di.UserSessionStateInputs
import com.wire.kalium.logic.feature.analytics.AnalyticsIdentifierManager
import com.wire.kalium.logic.feature.analytics.GetAnalyticsContactsDataUseCase
import com.wire.kalium.logic.feature.analytics.GetCurrentAnalyticsTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.analytics.ObserveAnalyticsTrackingIdentifierStatusUseCase
import com.wire.kalium.logic.feature.analytics.SetNewUserTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.app.AppScope
import com.wire.kalium.logic.feature.app.LegacyAppDependencies
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.backup.BackupScope
import com.wire.kalium.logic.feature.backup.LegacyBackupDependencies
import com.wire.kalium.logic.feature.backup.LegacyMultiPlatformBackupDependencies
import com.wire.kalium.logic.feature.backup.MultiPlatformBackupScope
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallsScope
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.feature.call.LegacyCallsDependencies
import com.wire.kalium.logic.feature.call.MediaManagerService
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.channels.ChannelsScope
import com.wire.kalium.logic.feature.channels.LegacyChannelsDependencies
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.LegacyClientDependencies
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.LegacyConnectionDependencies
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.GetConversationProtocolInfoUseCase
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.LeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCaseImpl
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.debug.LegacyDebugDependencies
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.e2ei.CheckCrlRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.legalhold.ApproveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.MarkLegalHoldChangeAsNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldChangeNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForSelfUserUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.feature.meeting.LegacyMeetingDependencies
import com.wire.kalium.logic.feature.meeting.MeetingScope
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.search.LegacySearchDependencies
import com.wire.kalium.logic.feature.search.SearchScope
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCase
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.service.LegacyServiceDependencies
import com.wire.kalium.logic.feature.service.ServiceScope
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.feature.team.LegacyTeamDependencies
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.IsPreventAdminlessGroupsEnabledUseCase
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.feature.user.LegacyUserDependencies
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkFileSharingChangeAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.feature.user.ObserveIsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.feature.user.SyncSelfUserUseCase
import com.wire.kalium.logic.feature.user.SyncUserPropertiesUseCase
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.feature.user.e2ei.MarkNotifyForRevokedCertificateAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.e2ei.ObserveShouldNotifyForRevokedCertificateUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.MarkGuestLinkFeatureFlagAsNotChangedUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.ObserveGuestRoomLinkFeatureFlagUseCase
import com.wire.kalium.logic.feature.user.migration.MigrateFromPersonalToTeamUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatus
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.network.guardedBy
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SendPendingMessagesUseCase
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncExecutorImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncShutdownCoordinator
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.SyncSuspension
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.local.LocalEventRepositoryImpl
import com.wire.kalium.logic.sync.periodic.MeetingOccurrencesSyncWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessWorker
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.AppMLSReceiveRecovery
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.recoveryOrThrow
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandler
import com.wire.kalium.logic.sync.receiving.CallPresentationRecord
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecovery
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecoveryStore
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.MockUnboundNetworkClient
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.message.MessageTimelineDAO
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkApis
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.work.LongWorkScope
import io.ktor.client.HttpClient
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@Suppress("LongParameterList", "LargeClass", "TooManyFunctions")
public class UserSessionScope internal constructor(
    userAgent: String,
    internal val userId: UserId,
    private val globalScope: GlobalKaliumScope,
    internal val globalCallManager: GlobalCallManager,
    private val globalDatabaseBuilder: GlobalDatabaseBuilder,
    private val globalPreferences: GlobalPrefProvider,
    authenticationScopeProvider: AuthenticationScopeProvider,
    internal val rootPathsProvider: RootPathsProvider,
    dataStoragePaths: DataStoragePaths,
    internal val kaliumConfigs: KaliumConfigs,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val userStorageProvider: UserStorageProvider,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider,
    private val clientConfig: ClientConfig,
    private val platformUserStorageProperties: PlatformUserStorageProperties,
    networkStateObserver: NetworkStateObserver,
    private val logoutCallback: LogoutCallback,
    private val syncShutdownCoordinator: SyncShutdownCoordinator,
    private val enableDurableMLSReceiveRecovery: Boolean = false,
    private val resourceAccess: com.wire.kalium.util.AccountResourceAccess? = null,
    private val coreCryptoCentralFactory: suspend (String, ByteArray) -> com.wire.kalium.cryptography.CoreCryptoCentral = { root, key ->
        com.wire.kalium.cryptography.coreCryptoCentral(root, key)
    },
) : CoroutineScope {
    private val userSessionResourceInputs: UserSessionResourceInputs
        get() = object : UserSessionResourceInputs {
            override val userStorage: UserStorage
                get() = this@UserSessionScope.userStorage
            override val authenticatedNetworkContainer: AuthenticatedNetworkContainer
                get() = this@UserSessionScope.authenticatedNetworkContainer
            override val sessionManager: SessionManager
                get() = this@UserSessionScope.sessionManager
            override val globalPreferences: GlobalPrefProvider
                get() = this@UserSessionScope.globalPreferences
            override val userStorageProvider: UserStorageProvider
                get() = this@UserSessionScope.userStorageProvider
            override val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider
                get() = this@UserSessionScope.userAuthenticatedNetworkProvider
            override val platformUserStorageProperties: PlatformUserStorageProperties
                get() = this@UserSessionScope.platformUserStorageProperties
            override val resourceAccess: com.wire.kalium.util.AccountResourceAccess?
                get() = this@UserSessionScope.resourceAccess
            override val proteusClientProvider: ProteusClientProvider
                get() = this@UserSessionScope.proteusClientProvider
            override val mlsClientProvider: MLSClientProvider
                get() = this@UserSessionScope.mlsClientProvider
            override val networkStateObserver: NetworkStateObserver
                get() = this@UserSessionScope.userSessionNetworkStateObserver
            override val dataStoragePaths: DataStoragePaths
                get() = this@UserSessionScope.userSessionDataStoragePaths
        }

    private val userSessionStateInputs: UserSessionStateInputs
        get() = object : UserSessionStateInputs {
            override val userId: UserId
                get() = this@UserSessionScope.userId
            override val kaliumConfigs: KaliumConfigs
                get() = this@UserSessionScope.kaliumConfigs
            override val clientConfig: ClientConfig
                get() = this@UserSessionScope.clientConfig
            override val userScopedLogger: KaliumLogger
                get() = this@UserSessionScope.userScopedLogger
            override val clientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val selfTeamId: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
            override val proposalTimersFlow: MutableSharedFlow<ProposalTimer>
                get() = this@UserSessionScope.proposalTimersFlow
            override val mlsMutex: Mutex
                get() = this@UserSessionScope.mlsMutex
            override val epochChangesObserver: EpochChangesObserver
                get() = this@UserSessionScope.epochChangesObserver
            override val notificationEventsManager: NotificationEventsManager
                get() = this@UserSessionScope.notificationEventsManager
            override val localEventRepository: LocalEventRepository
                get() = this@UserSessionScope.localEventRepository
            override val currentPersistenceEventHookNotifier: PersistenceEventHookNotifier
                get() = this@UserSessionScope.currentPersistenceEventHookNotifier
            override val currentCryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier
                get() = this@UserSessionScope.currentCryptoStateChangeHookNotifier
            override val nomadServiceUrl: String?
                get() = this@UserSessionScope.nomadServiceUrl
            override val invalidateTeamId: () -> Unit
                get() = this@UserSessionScope.invalidateTeamId
            override val enableDurableMLSReceiveRecovery: Boolean
                get() = this@UserSessionScope.enableDurableMLSReceiveRecovery
        }

    private val userSessionOwnershipInputs: UserSessionOwnershipInputs
        get() = object : UserSessionOwnershipInputs {
            override val sessionCoroutineScope: CoroutineScope
                get() = this@UserSessionScope
            override val globalCoroutineScope: CoroutineScope
                get() = this@UserSessionScope.globalScope
            override val callManager: Lazy<CallManager>
                get() = this@UserSessionScope.callManager
            override val userSessionScopeProvider: UserSessionScopeProvider
                get() = this@UserSessionScope.userSessionScopeProvider
            override val userSessionWorkScheduler: UserSessionWorkScheduler
                get() = this@UserSessionScope.userSessionWorkScheduler
            override val syncShutdownCoordinator: SyncShutdownCoordinator
                get() = this@UserSessionScope.syncShutdownCoordinator
            override val logoutCallback: LogoutCallback
                get() = this@UserSessionScope.logoutCallback
            override val receiveRecovery: suspend () -> AppMLSReceiveRecovery?
                get() = { this@UserSessionScope.receiveRecovery() }
            override val flowManagerService: FlowManagerService
                get() = this@UserSessionScope.flowManagerService
            override val mediaManagerService: MediaManagerService
                get() = this@UserSessionScope.mediaManagerService
        }

    private val userSessionGlobalInputs: UserSessionGlobalInputs
        get() = object : UserSessionGlobalInputs {
            override val globalSessionRepository: SessionRepository
                get() = this@UserSessionScope.globalScope.sessionRepository
            override val unboundNetworkContainer: UnboundNetworkContainer
                get() = this@UserSessionScope.globalScope.unboundNetworkContainer
            override val serverConfigRepository: ServerConfigRepository
                get() = this@UserSessionScope.authenticationScope.serverConfigRepository
        }

    private val userSessionNetworkStateObserver: NetworkStateObserver = networkStateObserver
    private val userSessionDataStoragePaths: DataStoragePaths = dataStoragePaths

    private val kaliumUserGraph by lazy {
        globalScope.kaliumGraph.userGraphFactory.create(
            dependencies = object : UserGraphDependencies {
                override fun createMessageRepository(): MessageRepository =
                    newMessageRepository(messageApiPagingCoordinator)
                override fun createConversationRepository(): ConversationRepository =
                    this@UserSessionScope.conversationRepository
            },
            legacyConversationDependencies = legacyConversationDependencies,
            legacyMessageDependencies = legacyMessageDependencies,
            legacyDebugDependencies = legacyDebugDependencies,
            legacyMeetingDependencies = legacyMeetingDependencies,
            legacyChannelsDependencies = legacyChannelsDependencies,
            legacySearchDependencies = legacySearchDependencies,
            legacyUserDependencies = legacyUserDependencies,
            legacyClientDependencies = legacyClientDependencies,
            userSessionResourceInputs = userSessionResourceInputs,
            userSessionStateInputs = userSessionStateInputs,
            userSessionOwnershipInputs = userSessionOwnershipInputs,
            userSessionGlobalInputs = userSessionGlobalInputs,
        )
    }

    // Admission exists before the graph is resolved. Invalidating an unused session never resolves it.
    private val consumers = KaliumConsumerRegistry { work -> kaliumUserGraph.consumerGraphFactory.create(work) }

    /** Individual conversation bindings owned by this user generation. */
    public val conversationApi: com.wire.kalium.logic.di.KaliumConversationApi
        get() = kaliumUserGraph

    /** Individual message bindings owned by this user generation. */
    public val messageApi: com.wire.kalium.logic.di.KaliumMessageApi
        get() = kaliumUserGraph

    /** Individual client bindings owned by this user generation. */
    public val clientApi: com.wire.kalium.logic.di.KaliumClientApi
        get() = kaliumUserGraph

    /** Individual user bindings owned by this user generation. */
    public val userApi: com.wire.kalium.logic.di.KaliumUserApi
        get() = kaliumUserGraph

    /** Individual search bindings owned by this user generation. */
    public val searchApi: com.wire.kalium.logic.di.KaliumSearchApi
        get() = kaliumUserGraph

    /** Individual channels bindings owned by this user generation. */
    public val channelsApi: com.wire.kalium.logic.di.KaliumChannelsApi
        get() = kaliumUserGraph

    /** Individual meeting bindings owned by this user generation. */
    public val meetingApi: com.wire.kalium.logic.di.KaliumMeetingApi
        get() = kaliumUserGraph

    /** Individual debug bindings owned by this user generation. */
    @com.wire.kalium.util.DebugKaliumApi("Debug-only individual user bindings.")
    public val debugApi: com.wire.kalium.logic.di.KaliumDebugApi
        get() = kaliumUserGraph

    /** Create one explicit owner per creator instance; callers close it after their work finishes. */
    public fun createConsumer(): KaliumConsumer = consumers.createConsumer()

    public val callsApiFactory: com.wire.kalium.logic.di.KaliumCallsApi.Factory
        get() = com.wire.kalium.logic.di.KaliumCallsApi.Factory {
            com.wire.kalium.logic.di.createCallsApi(
                kaliumUserGraph.legacyCallsGraphFactory,
                object : LegacyCallsDependencies {
                    override val callManager: Lazy<CallManager> = this@UserSessionScope.callManager
                    override val callBackgroundManager: CallBackgroundManager = this@UserSessionScope.callBackgroundManager
                    override val callRepository: CallRepository = this@UserSessionScope.callRepository
                    override val conversationRepository: ConversationRepository = this@UserSessionScope.conversationRepository
                    override val flowManagerService: FlowManagerService = this@UserSessionScope.flowManagerService
                    override val mediaManagerService: MediaManagerService = this@UserSessionScope.mediaManagerService
                    override val syncStateObserver: Lazy<SyncStateObserver> = this@UserSessionScope.syncStateObserver
                    override val qualifiedIdMapper: QualifiedIdMapper = this@UserSessionScope.qualifiedIdMapper
                    override val currentClientIdProvider: CurrentClientIdProvider = this@UserSessionScope.clientIdProvider
                    override val userConfigRepository: UserConfigRepository = this@UserSessionScope.userConfigRepository
                    override val getCallConversationType: GetCallConversationTypeProvider = this@UserSessionScope.getCallConversationType
                    override val conversationClientsInCallUpdater: ConversationClientsInCallUpdater =
                        this@UserSessionScope.conversationClientsInCallUpdater
                    override val kaliumConfigs: KaliumConfigs = this@UserSessionScope.kaliumConfigs
                    override val inCallReactionsRepository: InCallReactionsRepository = this@UserSessionScope.inCallReactionsRepository
                    override val callModerationActionsRepository: CallModerationActionsRepository =
                        this@UserSessionScope.callModerationActionsRepository
                    override val selfUserId: UserId = this@UserSessionScope.userId
                    override val userRepository: UserRepository = this@UserSessionScope.userRepository
                },
            )
        }

    public val backupApiFactory: com.wire.kalium.logic.di.KaliumBackupApi.Factory
        get() = com.wire.kalium.logic.di.KaliumBackupApi.Factory {
            com.wire.kalium.logic.di.createBackupApi(
                kaliumUserGraph.legacyBackupGraphFactory,
                object : LegacyBackupDependencies {
                    override val userId: UserId = this@UserSessionScope.userId
                    override val clientIdProvider: CurrentClientIdProvider = this@UserSessionScope.clientIdProvider
                    override val userRepository: UserRepository = this@UserSessionScope.userRepository
                    override val clientRepository: ClientRepository = this@UserSessionScope.clientRepository
                    override val eventRepository: EventRepository = this@UserSessionScope.eventRepository
                    override val kaliumFileSystem: KaliumFileSystem = this@UserSessionScope.kaliumFileSystem
                    override val userStorage: UserStorage = this@UserSessionScope.userStorage
                    override val cryptoTransactionProvider: CryptoTransactionProvider = this@UserSessionScope.cryptoTransactionProvider
                    override val globalPreferences: GlobalPrefProvider = this@UserSessionScope.globalPreferences
                    override val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository =
                        this@UserSessionScope.cryptoStateBackupRemoteRepository
                    override val rootPathsProvider: RootPathsProvider = this@UserSessionScope.rootPathsProvider
                    override val upgradeCurrentSession: UpgradeCurrentSessionUseCase = this@UserSessionScope.upgradeCurrentSessionUseCase
                    override val originatingScope: CoroutineScope = this@UserSessionScope
                    override val userSessionScopeProvider: UserSessionScopeProvider = this@UserSessionScope.userSessionScopeProvider
                },
            )
        }

    public val multiPlatformBackupApiFactory: com.wire.kalium.logic.di.KaliumMultiPlatformBackupApi.Factory
        get() = com.wire.kalium.logic.di.KaliumMultiPlatformBackupApi.Factory {
            com.wire.kalium.logic.di.createMultiPlatformBackupApi(
                kaliumUserGraph.legacyMultiPlatformBackupGraphFactory,
                object : LegacyMultiPlatformBackupDependencies {
                    override val selfUserId: UserId = this@UserSessionScope.userId
                    override val backupRepository: BackupRepository = this@UserSessionScope.backupRepository
                    override val userRepository: UserRepository = this@UserSessionScope.userRepository
                    override val kaliumFileSystem: KaliumFileSystem = this@UserSessionScope.kaliumFileSystem
                    override val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase =
                        this@UserSessionScope.kaliumUserGraph.conversationRefreshConversationsWithoutMetadata
                    override val userSessionScope: CoroutineScope = this@UserSessionScope
                    override val waitUntilSyncLive: suspend () -> Unit = this@UserSessionScope.syncManager::waitUntilLive
                },
            )
        }

    public val teamApiFactory: com.wire.kalium.logic.di.KaliumTeamApi.Factory
        get() = com.wire.kalium.logic.di.KaliumTeamApi.Factory {
            com.wire.kalium.logic.di.createTeamApi(
                kaliumUserGraph.teamFacadeGraphFactory,
                object : com.wire.kalium.logic.feature.team.LegacyTeamDependencies {
                    override val teamRepository: TeamRepository
                        get() = this@UserSessionScope.teamRepository
                    override val slowSyncRepository: SlowSyncRepository
                        get() = this@UserSessionScope.slowSyncRepository
                    override val selfTeamIdProvider: SelfTeamIdProvider
                        get() = this@UserSessionScope.selfTeamId
                },
            )
        }

    public val serviceApiFactory: com.wire.kalium.logic.di.KaliumServiceApi.Factory
        get() = com.wire.kalium.logic.di.KaliumServiceApi.Factory {
            com.wire.kalium.logic.di.createServiceApi(
                kaliumUserGraph.serviceFacadeGraphFactory,
                object : com.wire.kalium.logic.feature.service.LegacyServiceDependencies {
                    override val serviceRepository: ServiceRepository
                        get() = this@UserSessionScope.serviceRepository
                    override val teamRepository: TeamRepository
                        get() = this@UserSessionScope.teamRepository
                    override val userConfigRepository: UserConfigRepository
                        get() = this@UserSessionScope.userConfigRepository
                    override val selfTeamIdProvider: SelfTeamIdProvider
                        get() = this@UserSessionScope.selfTeamId
                },
            )
        }

    public val appApiFactory: com.wire.kalium.logic.di.KaliumAppApi.Factory
        get() = com.wire.kalium.logic.di.KaliumAppApi.Factory {
            com.wire.kalium.logic.di.createAppApi(
                kaliumUserGraph.appFacadeGraphFactory,
                object : com.wire.kalium.logic.feature.app.LegacyAppDependencies {
                    override val appRepository: AppRepository
                        get() = this@UserSessionScope.appRepository
                    override val selfTeamIdProvider: SelfTeamIdProvider
                        get() = this@UserSessionScope.selfTeamId
                },
                sessionManager.serverConfig().metaData.commonApiVersion.version,
            )
        }

    public val connectionApiFactory: com.wire.kalium.logic.di.KaliumConnectionApi.Factory
        get() = com.wire.kalium.logic.di.KaliumConnectionApi.Factory {
            com.wire.kalium.logic.di.createConnectionApi(
                kaliumUserGraph.connectionFacadeGraphFactory,
                object : com.wire.kalium.logic.feature.connection.LegacyConnectionDependencies {
                    override val connectionRepository: ConnectionRepository
                        get() = this@UserSessionScope.connectionRepository
                    override val conversationRepository: ConversationRepository
                        get() = this@UserSessionScope.conversationRepository
                    override val userRepository: UserRepository
                        get() = this@UserSessionScope.userRepository
                    override val oneOnOneResolver: OneOnOneResolver
                        get() = this@UserSessionScope.oneOnOneResolver
                    override val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
                        get() = this@UserSessionScope.newGroupConversationSystemMessagesCreator
                    override val fetchConversationUseCase: FetchConversationUseCase
                        get() = this@UserSessionScope.fetchConversationUseCase
                    override val transactionProvider: CryptoTransactionProvider
                        get() = this@UserSessionScope.cryptoTransactionProvider
                },
            )
        }

    internal fun invalidateConsumers() {
        consumers.invalidate()
    }

    internal suspend fun drainConsumers() {
        consumers.invalidateAndJoin()
    }

    private val notificationEventsManager = NotificationEventsManagerImpl()

    override val coroutineContext: CoroutineContext = SupervisorJob()

    internal val userStorage = userStorageProvider.getOrCreate(
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
            appMLSReceiveRecovery = null
        }
    }

    private suspend fun waitUntilClientIdIsAvailable() {
        if (_clientId == null) {
            clientRepository.observeCurrentClientId().filterNotNull().first()
        }
    }

    internal val callMapper: CallMapper get() = kaliumUserGraph.userSessionCallMapper

    public val qualifiedIdMapper: QualifiedIdMapper get() = kaliumUserGraph.userSessionQualifiedIdMapper

    public val federatedIdMapper: FederatedIdMapper
        get() = kaliumUserGraph.userSessionFederatedIdMapper

    private var persistenceEventHookNotifier: PersistenceEventHookNotifier? = null
    private var cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier? = null

    // Consumers keep a stable reference to these forwarding hooks. The actual Nomad hooks stay unset
    // until the session URL has been checked and a user-scoped hook pair has been created.
    private val currentPersistenceEventHookNotifier = object : PersistenceEventHookNotifier {
        override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onMessagePersisted(message, selfUserId)
        }

        override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onMessageDeleted(data, selfUserId)
        }

        override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onReactionPersisted(data, selfUserId)
        }

        override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onReadReceiptPersisted(data, selfUserId)
        }

        override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onConversationDeleted(data, selfUserId)
        }

        override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
            persistenceEventHookNotifier?.onConversationCleared(data, selfUserId)
        }

        override suspend fun onConversationLastReadPersisted(
            data: ConversationLastReadEventData,
            selfUserId: UserId
        ) {
            persistenceEventHookNotifier?.onConversationLastReadPersisted(data, selfUserId)
        }
    }
    private val currentCryptoStateChangeHookNotifier = CryptoStateChangeHookNotifier { changedUserId ->
        cryptoStateChangeHookNotifier?.onCryptoStateChanged(changedUserId)
    }
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider
        get() = kaliumUserGraph.userSessionIsClientAsyncNotificationsCapableProvider

    internal val clientIdProvider = CurrentClientIdProvider { clientId() }
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider by lazy {
        kaliumUserGraph.userSessionMlsSelfConversationIdProvider
    }
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider by lazy {
        kaliumUserGraph.userSessionProteusSelfConversationIdProvider
    }
    private val selfConversationIdProvider: SelfConversationIdProvider by
    lazy { kaliumUserGraph.userSessionSelfConversationIdProvider }

    private val proposalTimersFlow = MutableSharedFlow<ProposalTimer>()

    // TODO(refactor): Extract to Provider class and make atomic
    // val _teamId: Atomic<Either<CoreFailure, TeamId?>> = Atomic(Either.Left(CoreFailure.Unknown(Throwable("NotInitialized"))))
    private var _teamId: Either<CoreFailure, TeamId?> = Either.Left(CoreFailure.Unknown(Throwable("NotInitialized")))

    private suspend fun teamId(): Either<CoreFailure, TeamId?> = if (_teamId.isRight()) _teamId else {
        // this can depend directly on DAO it will make it easier to user
        // and remove any circular dependency when using this inside user repository
        wrapStorageNullableRequest {
            userStorage.database.userDAO.getTeamIdByQualifiedID(userId.toDao())
        }.map { teamId ->
            _teamId = Either.Right(teamId?.let { TeamId(it) })
            teamId?.let { TeamId(it) }
        }
    }

    private val invalidateTeamId = {
        _teamId = Either.Left(CoreFailure.Unknown(Throwable("NotInitialized")))
    }

    private val selfTeamId = SelfTeamIdProvider { teamId() }

    private val epochChangesObserver: EpochChangesObserver = EpochChangesObserverImpl()

    private val accessTokenRepository: AccessTokenRepository
        get() = kaliumUserGraph.userSessionAccessTokenRepository

    private val accessTokenRefresherFactory: AccessTokenRefresherFactory
        get() = kaliumUserGraph.userSessionAccessTokenRefresherFactory

    private val accessTokenRefresher: AccessTokenRefresher
        get() = kaliumUserGraph.userSessionAccessTokenRefresher

    private val sessionManager: SessionManager = SessionManagerImpl(
        sessionRepository = globalScope.sessionRepository,
        accessTokenRefresherFactory = accessTokenRefresherFactory,
        userId = userId,
        currentClientIdProvider = clientIdProvider,
        tokenStorage = globalPreferences.authTokenStorage,
        // Token failures can originate in a non-cancellable event worker that logout must drain.
        logout = { logoutReason -> logout(reason = logoutReason, waitUntilCompletes = false) }
    ).guardedBy(resourceAccess)

    /**
     * Keep the session's Nomad URL lookup in one place so both networking and hook setup share the same value.
     * SessionManager caches the backing account row, which avoids duplicate reads when this value is reused.
     */
    private val nomadServiceUrl: String? = sessionManager.nomadServiceUrl()?.takeIf { it.isNotBlank() }

    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer =
        userAuthenticatedNetworkProvider.getOrCreate(userId.toApi()) {
            UserAuthenticatedNetworkApis(
                container = AuthenticatedNetworkContainer.create(
                    sessionManager = sessionManager,
                    nomadServiceUrl = nomadServiceUrl,
                    selfUserId = UserIdDTO(userId.value, userId.domain),
                    userAgent = userAgent,
                    certificatePinning = kaliumConfigs.certPinningConfig,
                    mockEngine = kaliumConfigs.mockedRequests?.let { MockUnboundNetworkClient.createMockEngine(it) },
                    mockWebSocketSession = kaliumConfigs.mockedWebSocket?.session,
                    kaliumLogger = userScopedLogger,
                    httpTrafficObserver = kaliumConfigs.httpTrafficObserver,
                )
            )
        }.container

    private val featureSupport: FeatureSupport = kaliumUserGraph.userSessionFeatureSupport
    private val userScopedNomadHookFactory = kaliumUserGraph.userSessionUserScopedNomadHookFactory

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
        get() = kaliumUserGraph.userSessionUserConfigRepository

    private val userPropertyRepository: UserPropertyRepository
        get() = kaliumUserGraph.userSessionUserPropertyRepository

    private val keyPackageLimitsProvider: KeyPackageLimitsProvider
        get() = kaliumUserGraph.userSessionKeyPackageLimitsProvider

    private val proteusMigrationRecoveryHandler: ProteusMigrationRecoveryHandler by lazy {
        kaliumUserGraph.userSessionProteusMigrationRecoveryHandler
    }

    private val proteusClientProviderDelegate = lazy {
        ProteusClientProviderImpl(
            rootProteusPath = rootPathsProvider.rootProteusPath(userId),
            userId = userId,
            passphraseStorage = globalPreferences.passphraseStorage,
            proteusMigrationRecoveryHandler = proteusMigrationRecoveryHandler,
            currentClientIdProvider = clientIdProvider,
            coreCryptoCentralFactory = coreCryptoCentralFactory,
        ).guardedBy(resourceAccess)
    }

    internal val proteusClientProvider: ProteusClientProvider by proteusClientProviderDelegate

    private val localEventRepository: LocalEventRepository = LocalEventRepositoryImpl()

    private val mlsTransportProvider: MLSTransportProvider by lazy { kaliumUserGraph.userSessionMlsTransportProvider }

    private val mlsClientProviderDelegate = lazy {
        MLSClientProviderImpl(
            rootKeyStorePath = rootPathsProvider.rootMLSPath(userId),
            userId = userId,
            currentClientIdProvider = clientIdProvider,
            passphraseStorage = globalPreferences.passphraseStorage,
            userConfigRepository = userConfigRepository,
            featureConfigRepository = featureConfigRepository,
            mlsTransportProvider = mlsTransportProvider,
            epochObserver = epochChangesObserver,
            processingScope = this@UserSessionScope,
            coreCryptoCentralFactory = coreCryptoCentralFactory,
        ).guardedBy(resourceAccess)
    }

    internal val mlsClientProvider: MLSClientProvider by mlsClientProviderDelegate

    /** The host must first cancel/join public account operations and account/global observers. */
    internal suspend fun stopAccountWork() {
        UserSessionShutdown(consumers, coroutineContext.job, cellsLazy, callManager).stop()
    }

    /**
     * Stop sync and close its WebSocket client before another process may receive this account's events.
     * Legacy push skips clients the backend serves over an open WebSocket, and consumable
     * notifications need a single consumer. The native socket and the backend see the close
     * asynchronously. Sync stays stopped, even for new requests, until [resumeSync] or until
     * the returned suspension is cancelled. Failure leaves sync running.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun suspendSync(): SyncSuspension {
        val suspension = syncExecutorImpl.suspendExecution()
        try {
            authenticatedNetworkContainer.closeWebSockets()
        } catch (failure: Throwable) {
            suspension.cancel()
            throw failure
        }
        return suspension
    }

    /** Resume after ownership returns. */
    internal fun resumeSync() {
        syncExecutorImpl.resumeExecution()
    }

    internal suspend fun closeAccountResources() {
        stopAccountWork()
        if (proteusClientProviderDelegate.isInitialized()) proteusClientProviderDelegate.value.close()
        if (mlsClientProviderDelegate.isInitialized()) mlsClientProviderDelegate.value.close()
        authenticatedNetworkContainer.close()
        userStorage.database.close()
    }

    private val mlsMutex: Mutex = Mutex()

    private val mlsConversationRepository: MLSConversationRepository
        get() = kaliumUserGraph.userSessionMlsConversationRepository

    private val mlsMissingUsersRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler =
        kaliumUserGraph.userSessionMlsMissingUsersRejectionHandlerProvider.value

    private val e2eiRepository: E2EIRepository
        get() = kaliumUserGraph.userSessionE2eiRepository

    private val x509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider by lazy {
        kaliumUserGraph.userSessionX509CredentialAcquisitionConfigProvider
    }

    private val notificationTokenRepository get() = kaliumUserGraph.userSessionNotificationTokenRepository

    private val subconversationRepository =
        kaliumUserGraph.userSessionSubconversationRepository

    private val conversationRepository: ConversationRepository
        get() = kaliumUserGraph.userSessionConversationRepository

    private val conversationMetaDataRepository: ConversationMetaDataRepository
        get() = kaliumUserGraph.userSessionConversationMetaDataRepository

    private val conversationFolderRepository: ConversationFolderRepository
        get() = kaliumUserGraph.userSessionConversationFolderRepository

    private val conversationGroupRepository: ConversationGroupRepository
        get() = kaliumUserGraph.userSessionConversationGroupRepository

    private val newConversationMembersRepository: NewConversationMembersRepository
        get() = kaliumUserGraph.userSessionNewConversationMembersRepository

    private val messageRepository: MessageRepository
        get() = kaliumUserGraph.userSessionMessageRepository

    // The old cached MessageScope retained this coordinator through its captured repository.
    // New consumer wrappers borrow that same user-owned coordination; other factory captures stay fresh.
    private val messageApiPagingCoordinator: NomadMessagePagingCoordinator? by lazy {
        kaliumUserGraph.userSessionMessageApiPagingCoordinator
    }

    private fun newMessageRepository(pagingCoordinator: NomadMessagePagingCoordinator?): MessageRepository =
        kaliumUserGraph.userSessionMessageRepositoryFactory.create(pagingCoordinator)
    private val nomadMessagePagingCoordinator: NomadMessagePagingCoordinator?
        get() = kaliumUserGraph.userSessionNomadMessagePagingCoordinator

    private val messageMetadataRepository: MessageMetadataRepository
        get() = kaliumUserGraph.userSessionMessageMetadataRepository

    private val compositeMessageRepository: CompositeMessageRepository
        get() = kaliumUserGraph.userSessionCompositeMessageRepository

    private val pollMessageRepository: PollMessageRepository
        get() = kaliumUserGraph.userSessionPollMessageRepository

    private val messageDraftRepository: MessageDraftRepository by lazy { kaliumUserGraph.userSessionMessageDraftRepository }

    internal val slowSyncRepository: SlowSyncRepository by lazy { kaliumUserGraph.userSessionSlowSyncRepository }
    internal val incrementalSyncRepository: IncrementalSyncRepository by lazy { kaliumUserGraph.userSessionIncrementalSyncRepository }

    private val legalHoldSystemMessagesHandler = kaliumUserGraph.userSessionLegalHoldSystemMessagesHandler

    private val legalHoldHandler by lazy { kaliumUserGraph.userSessionLegalHoldHandler }

    private val userRepository: UserRepository
        get() = kaliumUserGraph.userSessionUserRepository

    private val accountRepository: AccountRepository
        get() = kaliumUserGraph.userSessionAccountRepository

    internal val pushTokenRepository: PushTokenRepository
        get() = kaliumUserGraph.userSessionPushTokenRepository

    private val teamRepository: TeamRepository
        get() = kaliumUserGraph.userSessionTeamRepository

    private val serviceRepository: ServiceRepository
        get() = kaliumUserGraph.userSessionServiceRepository

    private val appRepository: AppRepository
        get() = kaliumUserGraph.userSessionAppRepository

    private val persistConversationsUseCase: PersistConversationsUseCase
        get() = kaliumUserGraph.userSessionPersistConversationsUseCase

    private val persistConversationUseCase: PersistConversationUseCase
        get() = kaliumUserGraph.userSessionPersistConversationUseCase

    private val fetchMLSOneToOneConversationUseCase: FetchMLSOneToOneConversationUseCase
        get() = kaliumUserGraph.userSessionFetchMLSOneToOneConversationUseCase

    public val fetchConversationUseCase: FetchConversationUseCase
        get() = kaliumUserGraph.userSessionFetchConversationUseCase

    private val fetchConversationIfUnknownUseCase: FetchConversationIfUnknownUseCase
        get() = kaliumUserGraph.userSessionFetchConversationIfUnknownUseCase

    private val fetchConversationsUseCase: FetchConversationsUseCase
        get() = kaliumUserGraph.userSessionFetchConversationsUseCase

    private val connectionRepository: ConnectionRepository
        get() = kaliumUserGraph.userSessionConnectionRepository

    private val userSearchApiWrapper: UserSearchApiWrapper = kaliumUserGraph.userSessionUserSearchApiWrapper

    private val searchUserRepository: SearchUserRepository
        get() = kaliumUserGraph.userSessionSearchUserRepository

    public val backup: BackupScope
        get() = BackupScope(
            kaliumUserGraph.legacyBackupGraphFactory.create(object : LegacyBackupDependencies {
                override val userId: UserId = this@UserSessionScope.userId
                override val clientIdProvider: CurrentClientIdProvider = this@UserSessionScope.clientIdProvider
                override val userRepository: UserRepository = this@UserSessionScope.userRepository
                override val clientRepository: ClientRepository = this@UserSessionScope.clientRepository
                override val eventRepository: EventRepository = this@UserSessionScope.eventRepository
                override val kaliumFileSystem: KaliumFileSystem = this@UserSessionScope.kaliumFileSystem
                override val userStorage: UserStorage = this@UserSessionScope.userStorage
                override val cryptoTransactionProvider: CryptoTransactionProvider = this@UserSessionScope.cryptoTransactionProvider
                override val globalPreferences: GlobalPrefProvider = this@UserSessionScope.globalPreferences
                override val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository =
                    this@UserSessionScope.cryptoStateBackupRemoteRepository
                override val rootPathsProvider: RootPathsProvider = this@UserSessionScope.rootPathsProvider
                override val upgradeCurrentSession: UpgradeCurrentSessionUseCase = this@UserSessionScope.upgradeCurrentSessionUseCase
                override val originatingScope: CoroutineScope = this@UserSessionScope
                override val userSessionScopeProvider: UserSessionScopeProvider = this@UserSessionScope.userSessionScopeProvider
            }),
        )

    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository
        get() = kaliumUserGraph.userSessionCryptoStateBackupRemoteRepository

    public val multiPlatformBackup: MultiPlatformBackupScope
        get() = MultiPlatformBackupScope(
            kaliumUserGraph.legacyMultiPlatformBackupGraphFactory.create(object : LegacyMultiPlatformBackupDependencies {
                override val selfUserId: UserId = this@UserSessionScope.userId
                override val backupRepository: BackupRepository = this@UserSessionScope.backupRepository
                override val userRepository: UserRepository = this@UserSessionScope.userRepository
                override val kaliumFileSystem: KaliumFileSystem = this@UserSessionScope.kaliumFileSystem
                override val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase =
                    this@UserSessionScope.kaliumUserGraph.conversationRefreshConversationsWithoutMetadata
                override val userSessionScope: CoroutineScope = this@UserSessionScope
                override val waitUntilSyncLive: suspend () -> Unit = this@UserSessionScope.syncManager::waitUntilLive
            }),
        )

    internal val persistMessage: PersistMessageUseCase
        get() = kaliumUserGraph.userSessionPersistMessage

    private val addSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase
        get() = kaliumUserGraph.userSessionAddSystemMessageToAllConversationsUseCase

    private val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
        get() = kaliumUserGraph.userSessionRestartSlowSyncProcessForRecoveryUseCase

    private val callRepository: CallRepository by lazy { kaliumUserGraph.userSessionCallRepository }

    private val clientRemoteRepository: ClientRemoteRepository
        get() = kaliumUserGraph.userSessionClientRemoteRepository

    private val clientRegistrationStorage: ClientRegistrationStorage
        get() = kaliumUserGraph.userSessionClientRegistrationStorage

    internal val clientRepository: ClientRepository
        get() = kaliumUserGraph.userSessionClientRepository

    private val messageSendingScheduler: MessageSendingScheduler
        get() = kaliumUserGraph.userSessionMessageSendingScheduler

    private val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler
        get() = kaliumUserGraph.userSessionAudioNormalizedLoudnessScheduler

    private val assetRepository: AssetRepository
        get() = kaliumUserGraph.userSessionAssetRepository

    private val eventGatherer: EventGatherer
        get() = kaliumUserGraph.userSessionEventGatherer

    internal val cryptoTransactionProvider: CryptoTransactionProvider
        get() = kaliumUserGraph.userSessionCryptoTransactionProvider

    private val eventProcessor: EventProcessor by lazy { kaliumUserGraph.userSessionEventProcessor }

    private val slowSyncCriteriaProvider: SlowSyncCriteriaProvider
        get() = kaliumUserGraph.userSessionSlowSyncCriteriaProvider

    @Deprecated("Use syncStateObserver instead", ReplaceWith("syncStateObserver"))
    public val syncManager: SyncManager
        get() = kaliumUserGraph.userSessionSyncManager

    internal val syncStateObserver: Lazy<SyncStateObserver> = kaliumUserGraph.userSessionSyncStateObserver.value

    private val syncExecutorImpl: SyncExecutorImpl by lazy { kaliumUserGraph.userSessionSyncExecutorImpl }

    public val syncExecutor: SyncExecutor get() = kaliumUserGraph.userSessionSyncExecutor

    public fun setIncrementalSyncUnprocessedEventsBatchLimit(limit: Int?) {
        eventRepository.setUnprocessedEventsBatchLimit(limit)
    }

    private val syncConversations: SyncConversationsUseCase
        get() = kaliumUserGraph.userSessionSyncConversations

    private val updateConversationProtocolUseCase: UpdateConversationProtocolUseCase
        get() = kaliumUserGraph.userSessionUpdateConversationProtocolUseCase

    private val syncConnections: SyncConnectionsUseCase
        get() = kaliumUserGraph.userSessionSyncConnections

    private val syncSelfUser: SyncSelfUserUseCase get() = kaliumUserGraph.userSessionSyncSelfUser
    private val syncUserProperties: SyncUserPropertiesUseCase get() = kaliumUserGraph.userSessionSyncUserProperties
    private val syncContacts: SyncContactsUseCase get() = kaliumUserGraph.userSessionSyncContacts

    private val syncSelfTeamUseCase: SyncSelfTeamUseCase
        get() = kaliumUserGraph.userSessionSyncSelfTeamUseCase

    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase
        get() = kaliumUserGraph.userSessionJoinExistingMLSConversationUseCase

    private val registerMLSClientUseCase: RegisterMLSClientUseCase
        get() = kaliumUserGraph.userSessionRegisterMLSClientUseCase

    private val recoverMLSConversationsUseCase: RecoverMLSConversationsUseCase
        get() = kaliumUserGraph.userSessionRecoverMLSConversationsUseCase

    private val joinExistingMLSConversations: JoinExistingMLSConversationsUseCase
        get() = kaliumUserGraph.userSessionJoinExistingMLSConversations

    public val recoverMLSConversationsForUser: RecoverMLSConversationsForUserUseCase
        get() = kaliumUserGraph.userSessionRecoverMLSConversationsForUser

    private val joinSubconversationUseCase: JoinSubconversationUseCase
        get() = kaliumUserGraph.userSessionJoinSubconversationUseCase

    private val leaveSubconversationUseCase: LeaveSubconversationUseCase
        get() = kaliumUserGraph.userSessionLeaveSubconversationUseCase

    private val mlsOneOnOneConversationResolver: MLSOneOnOneConversationResolver
        get() = kaliumUserGraph.userSessionMlsOneOnOneConversationResolver

    private val oneOnOneMigrator: OneOnOneMigrator
        get() = kaliumUserGraph.userSessionOneOnOneMigrator
    private val pendingActionsRepository: PendingActionsRepository by lazy { kaliumUserGraph.userSessionPendingActionsRepository }
    private val oneOnOneResolver: OneOnOneResolver
        get() = kaliumUserGraph.userSessionOneOnOneResolver

    private val recoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase
        get() = kaliumUserGraph.userSessionRecoverPendingOneOnOneResolutionsUseCase

    private val recoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase
        get() = kaliumUserGraph.userSessionRecoverPendingMLSGroupJoinsUseCase

    private val updateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase
        get() = kaliumUserGraph.userSessionUpdateSupportedProtocols

    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
        get() = kaliumUserGraph.userSessionUpdateSupportedProtocolsAndResolveOneOnOnes

    private val slowSyncWorker: SlowSyncWorker by lazy { kaliumUserGraph.userSessionSlowSyncWorker }

    private val slowSyncRecoveryHandler: SlowSyncRecoveryHandler
        get() = kaliumUserGraph.userSessionSlowSyncRecoveryHandler

    private val syncMigrationStepsProvider: () -> SyncMigrationStepsProvider = kaliumUserGraph.userSessionSyncMigrationStepsProvider.value

    private val slowSyncManager: SlowSyncManager by lazy { kaliumUserGraph.userSessionSlowSyncManager }
    private val mlsConversationsRecoveryManager: MLSConversationsRecoveryManager by lazy {
        kaliumUserGraph.userSessionMlsConversationsRecoveryManager
    }

    private val joinPendingMLSConversationsManager: JoinPendingMLSConversationsManager by lazy {
        kaliumUserGraph.userSessionJoinPendingMLSConversationsManager
    }

    private val mlsFaultyKeysConversationsRepairUseCase: MLSFaultyKeysConversationsRepairUseCaseImpl by lazy {
        kaliumUserGraph.userSessionMlsFaultyKeysConversationsRepairUseCase
    }

    private val conversationsRecoveryManager: ConversationsRecoveryManager by lazy {
        kaliumUserGraph.userSessionConversationsRecoveryManager
    }

    private var appMLSReceiveRecovery: AppMLSReceiveRecovery? = null
    private val receiveRecoveryMutex = Mutex()

    // Apple constructs this scope only while holding the account lease. One cached coordinator
    // shares its operation index across handlers and batches for this registered client.
    private suspend fun receiveRecovery(): AppMLSReceiveRecovery? {
        if (!enableDurableMLSReceiveRecovery) return null
        return receiveRecoveryMutex.withLock {
            appMLSReceiveRecovery ?: AppMLSReceiveRecovery(
                recovery = MLSReceiveRecovery(
                    MLSReceiveRecoveryStore(
                        userStorage.database.metadataDAO,
                        userStorage.database.notificationEventStore,
                        clientId().recoveryOrThrow().value,
                    ),
                    authenticatedNetworkContainer.conversationApi,
                    userId,
                ) { context, groupId, groupInfo ->
                    check(context.joinByExternalCommit(groupInfo) == groupId) { "External join changed the MLS group" }
                    context.conversationEpoch(groupId)
                },
                conversations = conversationRepository,
                mlsConversations = mlsConversationRepository,
                persistMessage = persistMessage,
                selfUserId = userId,
                selfTeamId = selfTeamId,
            ).also { appMLSReceiveRecovery = it }
        }
    }

    private val incrementalSyncWorker: IncrementalSyncWorker by lazy { kaliumUserGraph.userSessionIncrementalSyncWorker }

    internal fun createIncrementalSyncWorker(eventGatherer: EventGatherer = this.eventGatherer): IncrementalSyncWorker =
        kaliumUserGraph.userSessionIncrementalWorkerFactory.create(eventGatherer)
    private val incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandlerImpl
        get() = kaliumUserGraph.userSessionIncrementalSyncRecoveryHandler

    private val incrementalSyncManager by lazy { kaliumUserGraph.userSessionIncrementalSyncManager }

    private val localEventManager by lazy { kaliumUserGraph.userSessionLocalEventManager }

    private val upgradeCurrentSessionUseCase
        get() = kaliumUserGraph.userSessionUpgradeCurrentSessionUseCase

    private val sessionRefreshSuggestedEventHandler: SessionRefreshSuggestedEventHandler
        get() = kaliumUserGraph.userSessionSessionRefreshSuggestedEventHandler

    @Suppress("MagicNumber")
    private val apiMigrations = kaliumUserGraph.userSessionApiMigrations

    private val apiMigrationManager
        get() = kaliumUserGraph.userSessionApiMigrationManager

    internal val eventRepository: EventRepository = kaliumUserGraph.userSessionEventRepository

    private val mlsMigrator: MLSMigrator
        get() = kaliumUserGraph.userSessionMlsMigrator

    internal val keyPackageManager: KeyPackageManager = kaliumUserGraph.userSessionKeyPackageManager

    internal val keyingMaterialsManager: KeyingMaterialsManager
        get() = kaliumUserGraph.userSessionKeyingMaterialsManager

    internal val mlsClientManager: MLSClientManager
        get() = kaliumUserGraph.userSessionMlsClientManager

    private val mlsMigrationWorker
        get() = kaliumUserGraph.userSessionMlsMigrationWorker

    internal val mlsMigrationManager: MLSMigrationManager
        get() = kaliumUserGraph.userSessionMlsMigrationManager

    private val mlsPublicKeysRepository: MLSPublicKeysRepository
        get() = kaliumUserGraph.userSessionMlsPublicKeysRepository

    private val videoStateChecker: VideoStateChecker get() = kaliumUserGraph.userSessionVideoStateChecker

    private val pendingSubconversationProposals: PendingSubconversationProposals by lazy {
        kaliumUserGraph.userSessionPendingSubconversationProposals
    }

    private val pendingProposalScheduler: PendingProposalScheduler =
        kaliumUserGraph.userSessionPendingProposalScheduler

    private val callManager: Lazy<CallManager> = lazy {
        globalCallManager.getCallManagerForClient(
            userId = userId,
            callRepository = callRepository,
            currentClientIdProvider = clientIdProvider,
            conversationRepository = conversationRepository,
            userConfigRepository = userConfigRepository,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = kaliumUserGraph.messageMessageSender,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            epochInfoUpdater = epochInfoUpdater,
            getCallConversationType = getCallConversationType,
            networkStateObserver = networkStateObserver,
            kaliumConfigs = kaliumConfigs,
            createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
        ).also { it.attachSignalJournal(CallSignalJournal(userStorage.database)) }
    }

    private val endCallOnMLSReset: EndCallOnMLSResetUseCase by lazy { kaliumUserGraph.userSessionEndCallOnMLSReset }

    internal val callBackgroundManager: CallBackgroundManager = kaliumUserGraph.userSessionCallBackgroundManager

    private val flowManagerService by lazy {
        globalCallManager.getFlowManager()
    }

    private val mediaManagerService by lazy {
        globalCallManager.getMediaManager()
    }

    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater
        get() = kaliumUserGraph.userSessionConversationClientsInCallUpdater

    private val epochInfoUpdater: EpochInfoUpdater
        get() = kaliumUserGraph.userSessionEpochInfoUpdater

    private val getCallConversationType: GetCallConversationTypeProvider by lazy { kaliumUserGraph.userSessionGetCallConversationType }

    private val updateConversationClientsForCurrentCall: Lazy<UpdateConversationClientsForCurrentCallUseCase>
        get() = kaliumUserGraph.userSessionUpdateConversationClientsForCurrentCall.value

    private val reactionRepository = kaliumUserGraph.userSessionReactionRepository
    private val receiptRepository = kaliumUserGraph.userSessionReceiptRepository
    private val persistReaction: PersistReactionUseCase
        get() = kaliumUserGraph.userSessionPersistReaction

    private val mlsUnpacker: MLSMessageUnpacker
        get() = kaliumUserGraph.userSessionMlsUnpacker

    private val proteusUnpacker: ProteusMessageUnpacker
        get() = kaliumUserGraph.userSessionProteusUnpacker

    private val messageEncoder get() = kaliumUserGraph.userSessionMessageEncoder

    private val systemMessageInserter get() = kaliumUserGraph.userSessionSystemMessageInserter

    private val receiptMessageHandler
        get() = kaliumUserGraph.userSessionReceiptMessageHandler

    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase
        get() = kaliumUserGraph.userSessionIsMessageSentInSelfConversation

    private val assetMessageHandler: AssetMessageHandler
        get() = kaliumUserGraph.userSessionAssetMessageHandler

    private val buttonActionConfirmationHandler: ButtonActionConfirmationHandler
        get() = kaliumUserGraph.userSessionButtonActionConfirmationHandler

    private val dataTransferEventHandler: DataTransferEventHandler
        get() = kaliumUserGraph.userSessionDataTransferEventHandler

    private val inCallReactionsRepository: InCallReactionsRepository by lazy { kaliumUserGraph.userSessionInCallReactionsRepository }

    private val callModerationActionsRepository: CallModerationActionsRepository by lazy {
        kaliumUserGraph.userSessionCallModerationActionsRepository
    }

    private val buttonActionHandler: ButtonActionHandler by lazy { kaliumUserGraph.userSessionButtonActionHandler }

    private val callingMessageHandler: CallingMessageHandler by lazy { kaliumUserGraph.userSessionCallingMessageHandler }

    private val applicationMessageHandler: ApplicationMessageHandler
        get() = kaliumUserGraph.userSessionApplicationMessageHandler

    private val linkPreviewsResolver: LinkPreviewImagesResolver by lazy { kaliumUserGraph.userSessionLinkPreviewsResolver }

    private val staleEpochVerifier: StaleEpochVerifier
        get() = kaliumUserGraph.userSessionStaleEpochVerifier

    private val newMessageHandler: NewMessageEventHandler
        get() = kaliumUserGraph.userSessionNewMessageHandler

    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
        get() = kaliumUserGraph.userSessionNewGroupConversationSystemMessagesCreator

    private val newConversationHandler: NewConversationEventHandler
        get() = kaliumUserGraph.userSessionNewConversationHandler
    private val deletedConversationHandler: DeletedConversationEventHandler
        get() = kaliumUserGraph.userSessionDeletedConversationHandler

    private val deleteConversationReminderEventHandler: DeleteConversationReminderEventHandler
        get() = kaliumUserGraph.userSessionDeleteConversationReminderEventHandler

    private val memberJoinHandler: MemberJoinEventHandler
        get() = kaliumUserGraph.userSessionMemberJoinHandler
    private val memberLeaveHandler: MemberLeaveEventHandler
        get() = kaliumUserGraph.userSessionMemberLeaveHandler
    private val memberChangeHandler: MemberChangeEventHandler
        get() = kaliumUserGraph.userSessionMemberChangeHandler
    private val mlsWelcomeHandler: MLSWelcomeEventHandler
        get() = kaliumUserGraph.userSessionMlsWelcomeHandler

    private val renamedConversationHandler: RenamedConversationEventHandler
        get() = kaliumUserGraph.userSessionRenamedConversationHandler

    private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler
        get() = kaliumUserGraph.userSessionReceiptModeUpdateEventHandler

    private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler
        get() = kaliumUserGraph.userSessionConversationMessageTimerEventHandler

    private val conversationCodeUpdateHandler: CodeUpdatedHandler
        get() = kaliumUserGraph.userSessionConversationCodeUpdateHandler

    private val conversationCodeDeletedHandler: CodeDeletedHandler
        get() = kaliumUserGraph.userSessionConversationCodeDeletedHandler

    private val typingIndicatorHandler: TypingIndicatorHandler
        get() = kaliumUserGraph.userSessionTypingIndicatorHandler

    private val protocolUpdateEventHandler: ProtocolUpdateEventHandler
        get() = kaliumUserGraph.userSessionProtocolUpdateEventHandler

    private val channelAddPermissionUpdateEventHandler: ChannelAddPermissionUpdateEventHandler
        get() = kaliumUserGraph.userSessionChannelAddPermissionUpdateEventHandler

    private val conversationAccessUpdateEventHandler: AccessUpdateEventHandler
        get() = kaliumUserGraph.userSessionConversationAccessUpdateEventHandler

    private val mlsResetConversationEventHandler: MLSResetConversationEventHandler
        get() = kaliumUserGraph.userSessionMlsResetConversationEventHandler

    private val conversationEventReceiver: ConversationEventReceiver by lazy { kaliumUserGraph.userSessionConversationEventReceiver }
    private val legalHoldRequestHandler = kaliumUserGraph.userSessionLegalHoldRequestHandler

    public val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase
        get() = kaliumUserGraph.userSessionObserveLegalHoldStateForUser

    public val observeAnalyticsTrackingIdentifierStatus: ObserveAnalyticsTrackingIdentifierStatusUseCase
        get() = kaliumUserGraph.userSessionObserveAnalyticsTrackingIdentifierStatus

    public val setNewUserTrackingIdentifier: SetNewUserTrackingIdentifierUseCase
        get() = kaliumUserGraph.userSessionSetNewUserTrackingIdentifier

    public val getCurrentAnalyticsTrackingIdentifier: GetCurrentAnalyticsTrackingIdentifierUseCase
        get() = kaliumUserGraph.userSessionGetCurrentAnalyticsTrackingIdentifier

    public val analyticsIdentifierManager: AnalyticsIdentifierManager
        get() = kaliumUserGraph.userSessionAnalyticsIdentifierManager

    public suspend fun observeIfE2EIRequiredDuringLogin(): Flow<Boolean?> = clientRepository.observeIsClientRegistrationBlockedByE2EI()

    public val observeLegalHoldForSelfUser: ObserveLegalHoldStateForSelfUserUseCase
        get() = kaliumUserGraph.userSessionObserveLegalHoldForSelfUser

    public val observeLegalHoldChangeNotifiedForSelf: ObserveLegalHoldChangeNotifiedForSelfUseCase
        get() = kaliumUserGraph.userSessionObserveLegalHoldChangeNotifiedForSelf

    public val markLegalHoldChangeAsNotifiedForSelf: MarkLegalHoldChangeAsNotifiedForSelfUseCase
        get() = kaliumUserGraph.userSessionMarkLegalHoldChangeAsNotifiedForSelf

    public val observeLegalHoldRequest: ObserveLegalHoldRequestUseCase
        get() = kaliumUserGraph.userSessionObserveLegalHoldRequest

    public val approveLegalHoldRequest: ApproveLegalHoldRequestUseCase
        get() = kaliumUserGraph.userSessionApproveLegalHoldRequest

    private val fetchUsersClientsFromRemote: FetchUsersClientsFromRemoteUseCase
        get() = kaliumUserGraph.userSessionFetchUsersClientsFromRemote

    internal val membersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase
        get() = kaliumUserGraph.userSessionMembersHavingLegalHoldClient

    private val fetchLegalHoldForSelfUserFromRemoteUseCase: FetchLegalHoldForSelfUserFromRemoteUseCase
        get() = kaliumUserGraph.userSessionFetchLegalHoldForSelfUserFromRemoteUseCase

    private val userEventReceiver: UserEventReceiver
        get() = kaliumUserGraph.userSessionUserEventReceiver

    private val userPropertiesEventReceiver: UserPropertiesEventReceiver
        get() = kaliumUserGraph.userSessionUserPropertiesEventReceiver

    private val federationEventReceiver: FederationEventReceiver
        get() = kaliumUserGraph.userSessionFederationEventReceiver

    private val teamEventReceiver: TeamEventReceiver
        get() = kaliumUserGraph.userSessionTeamEventReceiver

    private val guestRoomConfigHandler
        get() = kaliumUserGraph.userSessionGuestRoomConfigHandler

    private val fileSharingConfigHandler
        get() = kaliumUserGraph.userSessionFileSharingConfigHandler

    private val mlsConfigHandler
        get() = kaliumUserGraph.userSessionMlsConfigHandler

    private val mlsMigrationConfigHandler
        get() = kaliumUserGraph.userSessionMlsMigrationConfigHandler

    private val classifiedDomainsConfigHandler
        get() = kaliumUserGraph.userSessionClassifiedDomainsConfigHandler

    private val conferenceCallingConfigHandler
        get() = kaliumUserGraph.userSessionConferenceCallingConfigHandler

    private val consumableNotificationsConfigHandler
        get() = kaliumUserGraph.userSessionConsumableNotificationsConfigHandler

    private val appsFeatureHandler
        get() = kaliumUserGraph.userSessionAppsFeatureHandler

    private val secondFactorPasswordChallengeConfigHandler
        get() = kaliumUserGraph.userSessionSecondFactorPasswordChallengeConfigHandler

    private val selfDeletingMessagesConfigHandler
        get() = kaliumUserGraph.userSessionSelfDeletingMessagesConfigHandler

    private val e2eiConfigHandler
        get() = kaliumUserGraph.userSessionE2eiConfigHandler

    private val appLockConfigHandler
        get() = kaliumUserGraph.userSessionAppLockConfigHandler

    private val allowedGlobalOperationsHandler
        get() = kaliumUserGraph.userSessionAllowedGlobalOperationsHandler

    private val cellsConfigHandler
        get() = kaliumUserGraph.userSessionCellsConfigHandler

    private val enableUserProfileQRCodeConfigHandler
        get() = kaliumUserGraph.userSessionEnableUserProfileQRCodeConfigHandler

    private val assetAuditLogConfigHandler
        get() = kaliumUserGraph.userSessionAssetAuditLogConfigHandler

    private val preventAdminlessGroupsConfigHandler
        get() = kaliumUserGraph.userSessionPreventAdminlessGroupsConfigHandler

    private val meetingsConfigHandler
        get() = kaliumUserGraph.userSessionMeetingsConfigHandler

    private val featureConfigEventReceiver: FeatureConfigEventReceiver
        get() = kaliumUserGraph.userSessionFeatureConfigEventReceiver

    private val meetingCreateEventHandler: MeetingCreateEventHandler
        get() = kaliumUserGraph.userSessionMeetingCreateEventHandler

    private val meetingDeleteEventHandler: MeetingDeleteEventHandler
        get() = kaliumUserGraph.userSessionMeetingDeleteEventHandler

    private val meetingUpdateEventHandler: MeetingUpdateEventHandler
        get() = kaliumUserGraph.userSessionMeetingUpdateEventHandler

    private val meetingMemberAddEventHandler: MeetingMemberAddEventHandler
        get() = kaliumUserGraph.userSessionMeetingMemberAddEventHandler

    private val meetingEventReceiver: MeetingEventReceiver
        get() = kaliumUserGraph.userSessionMeetingEventReceiver

    private val preKeyRepository: PreKeyRepository
        get() = kaliumUserGraph.userSessionPreKeyRepository
    private val certificateRevocationListRepository: CertificateRevocationListRepository
        get() = kaliumUserGraph.userSessionCertificateRevocationListRepository

    private val proteusPreKeyRefiller: ProteusPreKeyRefiller
        get() = kaliumUserGraph.userSessionProteusPreKeyRefiller

    internal val userConfigSyncWorker: UserConfigSyncWorker by lazy { kaliumUserGraph.userSessionUserConfigSyncWorker }

    public val sendPendingMessages: SendPendingMessagesUseCase by lazy { kaliumUserGraph.userSessionSendPendingMessages }

    internal val meetingOccurrencesSyncWorker: MeetingOccurrencesSyncWorker = kaliumUserGraph.userSessionMeetingOccurrencesSyncWorker

    internal fun buildAudioNormalizedLoudnessWorker(
        conversationId: ConversationId,
        messageId: String
    ): AudioNormalizedLoudnessWorker = kaliumUserGraph.audioNormalizedLoudnessWorkerFactory.create(conversationId, messageId)

    private val keyPackageRepository: KeyPackageRepository
        get() = kaliumUserGraph.userSessionKeyPackageRepository

    private val logoutRepository: LogoutRepository = kaliumUserGraph.userSessionLogoutRepository

    private val backupRepository: BackupRepository
        get() = kaliumUserGraph.userSessionBackupRepository

    public val observeSyncState: ObserveSyncStateUseCase
        get() = kaliumUserGraph.userSessionObserveSyncState

    private val avsSyncStateReporter: AvsSyncStateReporter by lazy { kaliumUserGraph.userSessionAvsSyncStateReporter }

    private val protoContentMapper: ProtoContentMapper
        get() = kaliumUserGraph.userSessionProtoContentMapper

    private val oneOnOneProtocolSelector: OneOnOneProtocolSelector
        get() = kaliumUserGraph.userSessionOneOnOneProtocolSelector

    private val acmeCertificatesSyncUseCase: ACMECertificatesSyncUseCase by lazy { kaliumUserGraph.userSessionAcmeCertificatesSyncUseCase }

    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
        get() = kaliumUserGraph.userSessionRefreshUsersWithoutMetadata

    @OptIn(DelicateKaliumApi::class)
    public val client: ClientScope by lazy {
        ClientScope(kaliumUserGraph)
    }
    private val legacyConversationDependencies: LegacyConversationDependencies
        get() = object : LegacyConversationDependencies {
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val callRepository: CallRepository
                get() = this@UserSessionScope.callRepository
            override val conversationGroupRepository: ConversationGroupRepository
                get() = this@UserSessionScope.conversationGroupRepository
            override val connectionRepository: ConnectionRepository
                get() = this@UserSessionScope.connectionRepository
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val conversationFolderRepository: ConversationFolderRepository
                get() = this@UserSessionScope.conversationFolderRepository
            override val syncManager: SyncManager
                get() = this@UserSessionScope.syncManager
            override val mlsConversationRepository: MLSConversationRepository
                get() = this@UserSessionScope.mlsConversationRepository
            override val currentClientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val messageSender: MessageSender
                get() = this@UserSessionScope.kaliumUserGraph.messageMessageSender
            override val teamRepository: TeamRepository
                get() = this@UserSessionScope.teamRepository
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val selfUserId: UserId
                get() = this@UserSessionScope.userId
            override val selfConversationIdProvider: SelfConversationIdProvider
                get() = this@UserSessionScope.selfConversationIdProvider
            override val persistMessage: PersistMessageUseCase
                get() = this@UserSessionScope.persistMessage
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
            override val sendConfirmation: SendConfirmationUseCase
                get() = this@UserSessionScope.kaliumUserGraph.messageSendConfirmation
            override val renamedConversationHandler: RenamedConversationEventHandler
                get() = this@UserSessionScope.renamedConversationHandler
            override val serverConfigRepository: ServerConfigRepository
                get() = this@UserSessionScope.authenticationScope.serverConfigRepository
            override val userStorage: UserStorage
                get() = this@UserSessionScope.userStorage
            override val userPropertyRepository: UserPropertyRepository
                get() = this@UserSessionScope.userPropertyRepository
            override val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase
                get() = this@UserSessionScope.kaliumUserGraph.messageDeleteEphemeralMessageEndDate
            override val oneOnOneResolver: OneOnOneResolver
                get() = this@UserSessionScope.oneOnOneResolver
            override val scope: CoroutineScope
                get() = this@UserSessionScope
            override val kaliumLogger: KaliumLogger
                get() = this@UserSessionScope.userScopedLogger
            override val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
                get() = this@UserSessionScope.refreshUsersWithoutMetadata
            override val serverConfigLinks: ServerConfig.Links
                get() = this@UserSessionScope.sessionManager.getServerConfig().links
            override val messageRepository: MessageRepository
                get() = this@UserSessionScope.kaliumUserGraph.messageMessageRepository
            override val assetRepository: AssetRepository
                get() = this@UserSessionScope.assetRepository
            override val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
                get() = this@UserSessionScope.newGroupConversationSystemMessagesCreator
            override val deleteConversationUseCase: DeleteConversationUseCase
                get() = this@UserSessionScope.deleteConversationUseCase
            override val persistConversationsUseCase: PersistConversationsUseCase
                get() = this@UserSessionScope.persistConversationsUseCase
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
            override val resetMLSConversationUseCase: ResetMLSConversationUseCase
                get() = this@UserSessionScope.resetMlsConversation
            override val systemMessageInserter: SystemMessageInserter
                get() = this@UserSessionScope.systemMessageInserter
            override val persistenceEventHookNotifier: PersistenceEventHookNotifier
                get() = this@UserSessionScope.currentPersistenceEventHookNotifier
            override val memberJoinEventHandler: MemberJoinEventHandler
                get() = this@UserSessionScope.memberJoinHandler
            override val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
                get() = this@UserSessionScope.joinExistingMLSConversationUseCase
            override val pendingActionsRepository: PendingActionsRepository
                get() = this@UserSessionScope.pendingActionsRepository
            override val dispatcher: KaliumDispatcher
                get() = KaliumDispatcherImpl
        }

    public val conversations: ConversationScope by lazy { ConversationScope(kaliumUserGraph) }

    public val channels: ChannelsScope by lazy {
        ChannelsScope(kaliumUserGraph)
    }

    @DebugKaliumApi("Debug-only scope for developer tooling, QA, and profiling APIs.")
    public val debug: DebugScope by lazy {
        DebugScope(kaliumUserGraph)
    }

    private val legacyClientDependencies: LegacyClientDependencies
        get() = object : LegacyClientDependencies {
            override val clientRepository: ClientRepository
                get() = this@UserSessionScope.clientRepository
            override val pushTokenRepository: PushTokenRepository
                get() = this@UserSessionScope.pushTokenRepository
            override val logoutRepository: LogoutRepository
                get() = this@UserSessionScope.logoutRepository
            override val preKeyRepository: PreKeyRepository
                get() = this@UserSessionScope.preKeyRepository
            override val keyPackageRepository: KeyPackageRepository
                get() = this@UserSessionScope.keyPackageRepository
            override val keyPackageLimitsProvider: KeyPackageLimitsProvider
                get() = this@UserSessionScope.keyPackageLimitsProvider
            override val mlsClientProvider: MLSClientProvider
                get() = this@UserSessionScope.mlsClientProvider
            override val notificationTokenRepository: NotificationTokenRepository
                get() = this@UserSessionScope.notificationTokenRepository
            override val clientRemoteRepository: ClientRemoteRepository
                get() = this@UserSessionScope.clientRemoteRepository
            override val proteusClientProvider: ProteusClientProvider
                get() = this@UserSessionScope.proteusClientProvider
            override val sessionRepository: SessionRepository
                get() = this@UserSessionScope.globalScope.sessionRepository
            override val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase
                get() = this@UserSessionScope.upgradeCurrentSessionUseCase
            override val selfUserId: UserId
                get() = this@UserSessionScope.userId
            override val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
                get() = this@UserSessionScope.isAllowedToRegisterMLSClient
            override val clientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val secondFactorVerificationRepository: SecondFactorVerificationRepository
                get() = this@UserSessionScope.authenticationScope.secondFactorVerificationRepository
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val cachedClientIdClearer: CachedClientIdClearer
                get() = this@UserSessionScope.cachedClientIdClearer
            override val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
                get() = this@UserSessionScope.updateSupportedProtocolsAndResolveOneOnOnes
            override val registerMLSClientUseCase: RegisterMLSClientUseCase
                get() = this@UserSessionScope.registerMLSClientUseCase
            override val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
                get() = this@UserSessionScope.syncFeatureConfigsUseCase
            override val userConfigRepository: UserConfigRepository
                get() = this@UserSessionScope.userConfigRepository
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
            override val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier
                get() = this@UserSessionScope.currentCryptoStateChangeHookNotifier
            override val originatingScope: CoroutineScope
                get() = this@UserSessionScope
            override val userSessionScopeProvider: UserSessionScopeProvider
                get() = this@UserSessionScope.userSessionScopeProvider
        }

    private val legacyUserDependencies: LegacyUserDependencies
        get() = object : LegacyUserDependencies {
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val userConfigRepository: UserConfigRepository
                get() = this@UserSessionScope.userConfigRepository
            override val accountRepository: AccountRepository
                get() = this@UserSessionScope.accountRepository
            override val syncManager: SyncManager
                get() = this@UserSessionScope.syncManager
            override val assetRepository: AssetRepository
                get() = this@UserSessionScope.assetRepository
            override val teamRepository: TeamRepository
                get() = this@UserSessionScope.teamRepository
            override val sessionRepository: SessionRepository
                get() = this@UserSessionScope.globalScope.sessionRepository
            override val serverConfigRepository: ServerConfigRepository
                get() = this@UserSessionScope.authenticationScope.serverConfigRepository
            override val selfUserId: UserId
                get() = this@UserSessionScope.userId
            override val metadataDAO: MetadataDAO
                get() = this@UserSessionScope.userStorage.database.metadataDAO
            override val userPropertyRepository: UserPropertyRepository
                get() = this@UserSessionScope.userPropertyRepository
            override val messageSender: MessageSender
                get() = this@UserSessionScope.kaliumUserGraph.messageMessageSender
            override val clientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val registerMLSClientUseCase: RegisterMLSClientUseCase
                get() = this@UserSessionScope.registerMLSClientUseCase
            override val e2EIRepository: E2EIRepository
                get() = this@UserSessionScope.e2eiRepository
            override val mlsConversationRepository: MLSConversationRepository
                get() = this@UserSessionScope.mlsConversationRepository
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val isSelfATeamMember: IsSelfATeamMemberUseCase
                get() = this@UserSessionScope.team.isSelfATeamMember
            override val updateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase
                get() = this@UserSessionScope.updateSupportedProtocols
            override val clientRepository: ClientRepository
                get() = this@UserSessionScope.clientRepository
            override val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
                get() = this@UserSessionScope.refreshUsersWithoutMetadata
            override val isE2EIEnabledUseCase: IsE2EIEnabledUseCase
                get() = this@UserSessionScope.isE2EIEnabled
            override val incrementalSyncRepository: IncrementalSyncRepository
                get() = this@UserSessionScope.incrementalSyncRepository
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val sessionManager: SessionManager
                get() = this@UserSessionScope.sessionManager
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
            override val userScopedLogger: KaliumLogger
                get() = this@UserSessionScope.userScopedLogger
            override val teamUrlUseCase: GetTeamUrlUseCase
                get() = this@UserSessionScope.getTeamUrlUseCase
            override val isMLSEnabledUseCase: IsMLSEnabledUseCase
                get() = this@UserSessionScope.isMLSEnabled
            override val updateApiVersionsUseCase: UpdateApiVersionsUseCase
                get() = this@UserSessionScope.globalScope.updateApiVersions
            override val userConfigSyncWorker: UserConfigSyncWorker
                get() = this@UserSessionScope.userConfigSyncWorker
            override val mlsClientManager: MLSClientManager
                get() = this@UserSessionScope.mlsClientManager
            override val mlsMigrationManager: MLSMigrationManager
                get() = this@UserSessionScope.mlsMigrationManager
            override val keyingMaterialsManager: KeyingMaterialsManager
                get() = this@UserSessionScope.keyingMaterialsManager
            override val recoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase
                get() = this@UserSessionScope.recoverPendingMLSGroupJoinsUseCase
            override val recoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase
                get() = this@UserSessionScope.recoverPendingOneOnOneResolutionsUseCase
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
            override val userCoroutineScope: CoroutineScope
                get() = this@UserSessionScope
        }

    private val legacySearchDependencies: LegacySearchDependencies
        get() = object : LegacySearchDependencies {
            override val mlsPublicKeysRepository: MLSPublicKeysRepository
                get() = this@UserSessionScope.mlsPublicKeysRepository
            override val getDefaultProtocol: GetDefaultProtocolUseCase
                get() = this@UserSessionScope.getDefaultProtocol
            override val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
                get() = this@UserSessionScope.kaliumUserGraph.conversationGetConversationProtocolInfo
            override val searchUserRepository: SearchUserRepository
                get() = this@UserSessionScope.searchUserRepository
            override val sessionRepository: SessionRepository
                get() = this@UserSessionScope.globalScope.sessionRepository
            override val selfUserId: UserId
                get() = this@UserSessionScope.userId
            override val kaliumConfigs: KaliumConfigs
                get() = this@UserSessionScope.kaliumConfigs
        }

    private val legacyChannelsDependencies: LegacyChannelsDependencies
        get() = object : LegacyChannelsDependencies {
            override val selfUser: () -> GetSelfUserUseCase
                get() = { this@UserSessionScope.kaliumUserGraph.userGetSelfUser }
            override val conversationRepository: () -> ConversationRepository
                get() = { this@UserSessionScope.conversationRepository }
            override val metadataDaoProvider: () -> MetadataDAO
                get() = { this@UserSessionScope.userStorage.database.metadataDAO }
            override val selfUserObservationProvider: () -> SelfUserObservationProvider
                get() = { this@UserSessionScope.userRepository }
        }

    private val legacyMeetingDependencies: LegacyMeetingDependencies
        get() = object : LegacyMeetingDependencies {
            override val dispatcher: KaliumDispatcher
                get() = KaliumDispatcherImpl
            override val meetingRepository: MeetingRepository
                get() = this@UserSessionScope.meetingRepository
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
                get() = this@UserSessionScope.refreshUsersWithoutMetadata
            override val resetMLSConversation: ResetMLSConversationUseCase
                get() = this@UserSessionScope.resetMlsConversation
            override val joinExistingMLSConversation: JoinExistingMLSConversationUseCase
                get() = this@UserSessionScope.joinExistingMLSConversationUseCase
            override val leaveConversation: LeaveConversationUseCase
                get() = this@UserSessionScope.kaliumUserGraph.conversationLeaveConversation
            override val deleteConversation: DeleteConversationUseCase
                get() = this@UserSessionScope.deleteConversationUseCase
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
        }

    private val legacyDebugDependencies: LegacyDebugDependencies
        get() = object : LegacyDebugDependencies {
            override val messageRepository: MessageRepository
                get() = this@UserSessionScope.messageRepository
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val mlsConversationRepository: MLSConversationRepository
                get() = this@UserSessionScope.mlsConversationRepository
            override val joinExistingMLSConversationUseCaseProvider: () -> JoinExistingMLSConversationUseCase
                get() = { this@UserSessionScope.joinExistingMLSConversationUseCase }
            override val clientRepository: ClientRepository
                get() = this@UserSessionScope.clientRepository
            override val clientRemoteRepository: ClientRemoteRepository
                get() = this@UserSessionScope.clientRemoteRepository
            override val currentClientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val preKeyRepository: PreKeyRepository
                get() = this@UserSessionScope.preKeyRepository
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val featureConfigRepository: FeatureConfigRepository
                get() = this@UserSessionScope.featureConfigRepository
            override val userId: UserId
                get() = this@UserSessionScope.userId
            override val assetRepository: AssetRepository
                get() = this@UserSessionScope.assetRepository
            override val eventRepository: EventRepository
                get() = this@UserSessionScope.eventRepository
            override val syncManager: SyncManager
                get() = this@UserSessionScope.syncManager
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val messageSendingScheduler: MessageSendingScheduler
                get() = this@UserSessionScope.messageSendingScheduler
            override val selfConversationIdProvider: SelfConversationIdProvider
                get() = this@UserSessionScope.selfConversationIdProvider
            override val staleEpochVerifier: StaleEpochVerifier
                get() = this@UserSessionScope.staleEpochVerifier
            override val eventProcessor: EventProcessor
                get() = this@UserSessionScope.eventProcessor
            override val legalHoldHandler: LegalHoldHandler
                get() = this@UserSessionScope.legalHoldHandler
            override val notificationTokenRepository: NotificationTokenRepository
                get() = this@UserSessionScope.notificationTokenRepository
            override val scope: CoroutineScope
                get() = this@UserSessionScope
            override val userStorage: UserStorage
                get() = this@UserSessionScope.userStorage
            override val mlsMissingUsersMessageRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler
                get() = this@UserSessionScope.mlsMissingUsersRejectionHandlerProvider
            override val x509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider
                get() = this@UserSessionScope.x509CredentialAcquisitionConfigProvider
            override val fetchConversationUseCase: FetchConversationUseCase
                get() = this@UserSessionScope.fetchConversationUseCase
            override val resetMLSConversationUseCase: ResetMLSConversationUseCase
                get() = this@UserSessionScope.resetMlsConversation
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
            override val refillKeyPackagesUseCase: RefillKeyPackagesUseCase
                get() = this@UserSessionScope.kaliumUserGraph.clientRefillKeyPackages
            override val certificateRevocationListRepository: CertificateRevocationListRepository
                get() = this@UserSessionScope.certificateRevocationListRepository
            override val logger: KaliumLogger
                get() = this@UserSessionScope.userScopedLogger
            override val dispatcher: KaliumDispatcher
                get() = KaliumDispatcherImpl
        }

    private val legacyMessageDependencies: LegacyMessageDependencies
        get() = object : LegacyMessageDependencies {
            override val notificationEventsManager: NotificationEventsManager
                get() = this@UserSessionScope.notificationEventsManager
            override val connectionRepository: ConnectionRepository
                get() = this@UserSessionScope.connectionRepository
            override val messageDraftRepository: MessageDraftRepository
                get() = this@UserSessionScope.messageDraftRepository
            override val selfUserId: QualifiedID
                get() = this@UserSessionScope.userId
            override val currentClientIdProvider: CurrentClientIdProvider
                get() = this@UserSessionScope.clientIdProvider
            override val selfConversationIdProvider: SelfConversationIdProvider
                get() = this@UserSessionScope.selfConversationIdProvider
            override val messageRepository: MessageRepository
                get() = newMessageRepository(messageApiPagingCoordinator)
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val attachmentsRepository: Lazy<MessageAttachmentDraftRepository>
                get() = lazy { this@UserSessionScope.cells.messageAttachmentsDraftRepository }
            override val mlsConversationRepository: MLSConversationRepository
                get() = this@UserSessionScope.mlsConversationRepository
            override val clientRepository: ClientRepository
                get() = this@UserSessionScope.clientRepository
            override val clientRemoteRepository: ClientRemoteRepository
                get() = this@UserSessionScope.clientRemoteRepository
            override val preKeyRepository: PreKeyRepository
                get() = this@UserSessionScope.preKeyRepository
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val assetRepository: AssetRepository
                get() = this@UserSessionScope.assetRepository
            override val reactionRepository: ReactionRepository
                get() = this@UserSessionScope.reactionRepository
            override val receiptRepository: ReceiptRepository
                get() = this@UserSessionScope.receiptRepository
            override val syncManager: SyncManager
                get() = this@UserSessionScope.syncManager
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val messageSendingScheduler: MessageSendingScheduler
                get() = this@UserSessionScope.messageSendingScheduler
            override val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler
                get() = this@UserSessionScope.audioNormalizedLoudnessScheduler
            override val userPropertyRepository: UserPropertyRepository
                get() = this@UserSessionScope.userPropertyRepository
            override val incrementalSyncRepository: IncrementalSyncRepository
                get() = this@UserSessionScope.incrementalSyncRepository
            override val kaliumFileSystem: KaliumFileSystem
                get() = this@UserSessionScope.kaliumFileSystem
            override val protoContentMapper: ProtoContentMapper
                get() = this@UserSessionScope.protoContentMapper
            override val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
                get() = this@UserSessionScope.observeSelfDeletingMessages
            override val messageMetadataRepository: MessageMetadataRepository
                get() = this@UserSessionScope.messageMetadataRepository
            override val staleEpochVerifier: StaleEpochVerifier
                get() = this@UserSessionScope.staleEpochVerifier
            override val legalHoldHandler: LegalHoldHandler
                get() = this@UserSessionScope.legalHoldHandler
            override val observeFileSharingStatusUseCase: ObserveFileSharingStatusUseCase
                get() = this@UserSessionScope.observeFileSharingStatus
            override val getMessageAttachmentsUseCase: Lazy<GetMessageAttachmentsUseCase>
                get() = lazy { this@UserSessionScope.cells.getMessageAttachmentsUseCase }
            override val publishAttachmentsUseCase: Lazy<PublishAttachmentsUseCase>
                get() = lazy { this@UserSessionScope.cells.publishAttachments }
            override val removeAttachmentDraftsUseCase: Lazy<RemoveAttachmentDraftsUseCase>
                get() = lazy { this@UserSessionScope.cells.removeAttachments }
            override val deleteMessageAttachmentsUseCase: Lazy<DeleteMessageAttachmentsUseCase>
                get() = lazy { this@UserSessionScope.cells.deleteAttachmentsUseCase }
            override val fetchConversationUseCase: FetchConversationUseCase
                get() = this@UserSessionScope.fetchConversationUseCase
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
            override val compositeMessageRepository: CompositeMessageRepository
                get() = this@UserSessionScope.compositeMessageRepository
            override val pollMessageRepository: PollMessageRepository
                get() = this@UserSessionScope.pollMessageRepository
            override val joinExistingConversationUseCaseProvider: () -> JoinExistingMLSConversationUseCase
                get() = { this@UserSessionScope.joinExistingMLSConversationUseCase }
            override val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
                get() = this@UserSessionScope.globalScope.audioNormalizedLoudnessBuilder
            override val mlsMissingUsersMessageRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler
                get() = this@UserSessionScope.mlsMissingUsersRejectionHandlerProvider
            override val persistenceEventHookNotifier: PersistenceEventHookNotifier
                get() = this@UserSessionScope.currentPersistenceEventHookNotifier
            override val linkPreviewEnabled: Boolean
                get() = this@UserSessionScope.kaliumConfigs.linkPreviewEnabled
            override val scope: CoroutineScope
                get() = this@UserSessionScope
            override val kaliumConfigs: KaliumConfigs
                get() = this@UserSessionScope.kaliumConfigs
            override val kaliumLogger: KaliumLogger
                get() = this@UserSessionScope.userScopedLogger
            override val dispatcher: KaliumDispatcher
                get() = KaliumDispatcherImpl
            override val messageTimelineDAO: MessageTimelineDAO
                get() = this@UserSessionScope.userStorage.database.messageTimelineDAO
        }

    public val messages: MessageScope by lazy { MessageScope(kaliumUserGraph) }

    public val users: UserScope by lazy {
        UserScope(kaliumUserGraph)
    }

    public val search: SearchScope by lazy {
        SearchScope(kaliumUserGraph)
    }

    private val clearUserData: ClearUserDataUseCase get() = kaliumUserGraph.userSessionClearUserData

    private val validateAssetMimeType: ValidateAssetFileTypeUseCase get() = kaliumUserGraph.userSessionValidateAssetMimeType

    public val logout: LogoutUseCase
        get() = kaliumUserGraph.userSessionLogout
    public val persistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase
        get() = kaliumUserGraph.userSessionPersistPersistentWebSocketConnectionStatus

    public val getPersistentWebSocketStatus: GetPersistentWebSocketStatus
        get() = kaliumUserGraph.userSessionGetPersistentWebSocketStatus

    private val featureConfigRepository: FeatureConfigRepository
        get() = kaliumUserGraph.userSessionFeatureConfigRepository
    public val isFileSharingEnabled: IsFileSharingEnabledUseCase get() = kaliumUserGraph.userSessionIsFileSharingEnabled

    public val isPreventAdminlessGroupsEnabled: IsPreventAdminlessGroupsEnabledUseCase
        get() = kaliumUserGraph.userSessionIsPreventAdminlessGroupsEnabled

    public val isMeetingsEnabled: IsMeetingsEnabledUseCase
        get() = kaliumUserGraph.userSessionIsMeetingsEnabled

    public val observeIsMeetingsEnabled: ObserveIsMeetingsEnabledUseCase
        get() = kaliumUserGraph.userSessionObserveIsMeetingsEnabled

    public val observeFileSharingStatus: ObserveFileSharingStatusUseCase
        get() = kaliumUserGraph.userSessionObserveFileSharingStatus

    public val observeShouldNotifyForRevokedCertificate: ObserveShouldNotifyForRevokedCertificateUseCase
            by lazy { kaliumUserGraph.userSessionObserveShouldNotifyForRevokedCertificate }

    public val markNotifyForRevokedCertificateAsNotified: MarkNotifyForRevokedCertificateAsNotifiedUseCase
            by lazy { kaliumUserGraph.userSessionMarkNotifyForRevokedCertificateAsNotified }

    public val markGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase
        get() = kaliumUserGraph.userSessionMarkGuestLinkFeatureFlagAsNotChanged

    public val appLockTeamFeatureConfigObserver: AppLockTeamFeatureConfigObserver
        get() = kaliumUserGraph.userSessionAppLockTeamFeatureConfigObserver

    public val markTeamAppLockStatusAsNotified: MarkTeamAppLockStatusAsNotifiedUseCase
        get() = kaliumUserGraph.userSessionMarkTeamAppLockStatusAsNotified

    public val markSelfDeletingMessagesAsNotified: MarkSelfDeletionStatusAsNotifiedUseCase
        get() = kaliumUserGraph.userSessionMarkSelfDeletingMessagesAsNotified

    public val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
        get() = kaliumUserGraph.userSessionObserveSelfDeletingMessages

    public val observeTeamSettingsSelfDeletionStatus: ObserveTeamSettingsSelfDeletingStatusUseCase
        get() = kaliumUserGraph.userSessionObserveTeamSettingsSelfDeletionStatus

    public val persistNewSelfDeletionStatus: PersistNewSelfDeletionTimerUseCase
        get() = kaliumUserGraph.userSessionPersistNewSelfDeletionStatus

    public val observeGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase
        get() = kaliumUserGraph.userSessionObserveGuestRoomLinkFeatureFlag

    public val markFileSharingStatusAsNotified: MarkFileSharingChangeAsNotifiedUseCase
        get() = kaliumUserGraph.userSessionMarkFileSharingStatusAsNotified

    public val isMLSEnabled: IsMLSEnabledUseCase get() = kaliumUserGraph.userSessionIsMLSEnabled
    public val isE2EIEnabled: IsE2EIEnabledUseCase
        get() = kaliumUserGraph.userSessionIsE2EIEnabled

    public val getDefaultProtocol: GetDefaultProtocolUseCase
        get() = kaliumUserGraph.userSessionGetDefaultProtocol

    public val observeE2EIRequired: ObserveE2EIRequiredUseCase
        get() = kaliumUserGraph.userSessionObserveE2EIRequired
    public val markE2EIRequiredAsNotified: MarkEnablingE2EIAsNotifiedUseCase
        get() = kaliumUserGraph.userSessionMarkE2EIRequiredAsNotified

    @OptIn(DelicateKaliumApi::class)
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase
        get() = kaliumUserGraph.userSessionIsAllowedToRegisterMLSClient

    private val syncFeatureConfigsUseCase: SyncFeatureConfigsUseCase
        get() = kaliumUserGraph.userSessionSyncFeatureConfigsUseCase

    private val legacyTeamDependencies: LegacyTeamDependencies
        get() = object : LegacyTeamDependencies {
            override val teamRepository: TeamRepository
                get() = this@UserSessionScope.teamRepository
            override val slowSyncRepository: SlowSyncRepository
                get() = this@UserSessionScope.slowSyncRepository
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
        }

    private val legacyServiceDependencies: LegacyServiceDependencies
        get() = object : LegacyServiceDependencies {
            override val serviceRepository: ServiceRepository
                get() = this@UserSessionScope.serviceRepository
            override val teamRepository: TeamRepository
                get() = this@UserSessionScope.teamRepository
            override val userConfigRepository: UserConfigRepository
                get() = this@UserSessionScope.userConfigRepository
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
        }

    private val legacyAppDependencies: LegacyAppDependencies
        get() = object : LegacyAppDependencies {
            override val appRepository: AppRepository
                get() = this@UserSessionScope.appRepository
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = this@UserSessionScope.selfTeamId
        }

    private val legacyConnectionDependencies: LegacyConnectionDependencies
        get() = object : LegacyConnectionDependencies {
            override val connectionRepository: ConnectionRepository
                get() = this@UserSessionScope.connectionRepository
            override val conversationRepository: ConversationRepository
                get() = this@UserSessionScope.conversationRepository
            override val userRepository: UserRepository
                get() = this@UserSessionScope.userRepository
            override val oneOnOneResolver: OneOnOneResolver
                get() = this@UserSessionScope.oneOnOneResolver
            override val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
                get() = this@UserSessionScope.newGroupConversationSystemMessagesCreator
            override val fetchConversationUseCase: FetchConversationUseCase
                get() = this@UserSessionScope.fetchConversationUseCase
            override val transactionProvider: CryptoTransactionProvider
                get() = this@UserSessionScope.cryptoTransactionProvider
        }

    public val team: TeamScope
        get() = TeamScope(kaliumUserGraph.teamFacadeGraphFactory.create(legacyTeamDependencies))

    public val service: ServiceScope
        get() = ServiceScope(kaliumUserGraph.serviceFacadeGraphFactory.create(legacyServiceDependencies))

    public val apps: AppScope
        get() = AppScope(
            kaliumUserGraph.appFacadeGraphFactory.create(
                legacyAppDependencies,
                sessionManager.serverConfig().metaData.commonApiVersion.version,
            )
        )

    /** Resume NSE-committed signaling using this session's existing full calling engine. */
    public suspend fun resumePendingCallSignals(): Unit = callManager.value.replayPendingSignals()

    /** Prioritize the exact delivered Apple call, preserving all signaling in its conversation. */
    public suspend fun resumeIncomingCallSignals(callId: String): Unit = callManager.value.replayIncomingCallSignals(callId)

    /** Register the identities of delivered Apple alerts before observing their canonical state. */
    public suspend fun rememberNotificationMessages(messages: List<NotificationMessageKey>): Unit =
        userStorage.database.messageDAO.rememberNotificationMessages(messages)

    /** Uses the existing account database owner, also observing sync performed by the app. */
    public fun observeNotificationRemovalCandidates(
        messageIds: List<String>,
    ): Flow<List<NotificationMessageKey>> =
        userStorage.database.messageDAO.observeNotificationRemovalCandidates(messageIds)

    public fun observeCallPresentations(): Flow<List<CallPresentationRecord>> =
        kaliumUserGraph.userSessionCallSignalJournal.observePresentations()

    public suspend fun callPresentations(): List<CallPresentationRecord> =
        kaliumUserGraph.userSessionCallSignalJournal.presentations()

    public val calls: CallsScope
        get() = CallsScope(kaliumUserGraph.userSessionFreshCallsOperation)

    public val connection: ConnectionScope
        get() = ConnectionScope(kaliumUserGraph.connectionFacadeGraphFactory.create(legacyConnectionDependencies))

    public val observeSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase
        get() = kaliumUserGraph.userSessionObserveSecurityClassificationLabel

    public val getOtherUserSecurityClassificationLabel: ObserveOtherUserSecurityClassificationLabelUseCase
        get() = kaliumUserGraph.userSessionGetOtherUserSecurityClassificationLabel

    public val persistScreenshotCensoringConfig: PersistScreenshotCensoringConfigUseCase
        get() = kaliumUserGraph.userSessionPersistScreenshotCensoringConfig

    public val observeScreenshotCensoringConfig: ObserveScreenshotCensoringConfigUseCase
        get() = kaliumUserGraph.userSessionObserveScreenshotCensoringConfig

    public val fetchConversationMLSVerificationStatus: FetchConversationMLSVerificationStatusUseCase
        get() = kaliumUserGraph.userSessionFetchConversationMLSVerificationStatus

    public val kaliumFileSystem: KaliumFileSystem by lazy { kaliumUserGraph.userSessionKaliumFileSystem }

    public val checkCrlRevocationList: CheckCrlRevocationListUseCase
        get() = kaliumUserGraph.userSessionCheckCrlRevocationList

    private val createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
        get() = kaliumUserGraph.userSessionCreateAndPersistRecentlyEndedCallMetadata

    public val migrateFromPersonalToTeam: MigrateFromPersonalToTeamUseCase
        get() = kaliumUserGraph.userSessionMigrateFromPersonalToTeam

    internal val getProxyCredentials: GetProxyCredentialsUseCase
        get() = kaliumUserGraph.userSessionGetProxyCredentials

    private fun createPushTokenUpdater() = kaliumUserGraph.userSessionPushTokenUpdater

    private val fetchMLSVerificationStatusUseCase: FetchMLSVerificationStatusUseCase by lazy {
        kaliumUserGraph.userSessionFetchMLSVerificationStatusUseCase
    }

    private val observeE2EIConversationsVerificationStatuses: ObserveE2EIConversationsVerificationStatusesUseCase by lazy {
        kaliumUserGraph.userSessionObserveE2EIConversationsVerificationStatuses
    }

    private val typingIndicatorSyncManager: TypingIndicatorSyncManager =
        kaliumUserGraph.userSessionTypingIndicatorSyncManager

    private val analyticsRepository: AnalyticsRepository
        get() = kaliumUserGraph.userSessionAnalyticsRepository

    public val getTeamUrlUseCase: GetTeamUrlUseCase
        get() = kaliumUserGraph.userSessionGetTeamUrlUseCase

    public val getAnalyticsContactsData: GetAnalyticsContactsDataUseCase
        get() = kaliumUserGraph.userSessionGetAnalyticsContactsData

    private val cellsLazy = lazy {
        CellsScope(
            cellsClient = cellsClient,
            dao = with(userStorage.database) {
                CellsScope.CellScopeDao(
                    attachmentDraftDao = messageAttachmentDraftDao,
                    conversationsDao = conversationDAO,
                    attachmentsDao = messageAttachments,
                    cellFileDao = cellFileDao,
                    userDao = userDAO,
                    memberDao = memberDAO,
                    publicLinkDao = publicLinks,
                    userConfigDAO = userConfigDAO,
                )
            },
            sessionManager = sessionManager,
            accessTokenApi = authenticatedNetworkContainer.accessTokenApi,
            userId = userId,
            networkStateObserver = networkStateObserver,
        )
    }
    public val cells: CellsScope by cellsLazy

    public val isCellsInitialized: Boolean get() = cellsLazy.isInitialized()

    private val deleteConversationUseCase: DeleteConversationUseCase
        get() = kaliumUserGraph.userSessionDeleteConversationUseCase

    internal val userSessionWorkScheduler: UserSessionWorkScheduler = globalScope.workSchedulerProvider.userSessionWorkScheduler(this)

    public val resetMlsConversation: ResetMLSConversationUseCase
        get() = kaliumUserGraph.userSessionResetMlsConversation

    public val migrateConversationToMLS: MigrateConversationToMLSUseCase
        get() = kaliumUserGraph.userSessionMigrateConversationToMLS

    public val longWork: LongWorkScope = LongWorkScope.prepareForRuntime(
        { this },
        { slowSyncRepository.slowSyncStatus.map { it is SlowSyncStatus.Ongoing } }
    )

    public val meetings: MeetingScope by lazy {
        MeetingScope(kaliumUserGraph)
    }

    private val meetingRepository: MeetingRepository
        get() = kaliumUserGraph.userSessionMeetingRepository

    public val syncMeetingsUseCase: SyncMeetingsUseCase
        get() = kaliumUserGraph.userSessionSyncMeetingsUseCase

    private fun registerNomadHooksIfConfigured() {
        val hooks = userScopedNomadHookFactory.createIfConfigured(
            selfUserId = userId,
            nomadServiceUrl = nomadServiceUrl,
            userStorageProvider = userStorageProvider,
            userAuthenticatedNetworkProvider = userAuthenticatedNetworkProvider,
            scope = this,
            backup = {
                backup.backupAndUploadCryptoState()
            }
        ) ?: return

        // Both hooks are instantiated inside the session scope so they stay bound to this user only.
        persistenceEventHookNotifier = hooks.persistence
        cryptoStateChangeHookNotifier = hooks.crypto
    }

    /**
     * This will start subscribers of observable work per user session, as long as the user is logged in.
     * When the user logs out, this work will be canceled.
     */
    private val runtime by lazy {
        UserSessionRuntime(
            scope = this,
            logger = userScopedLogger,
            startInitialSyncMonitoring = { longWork.startInitialSyncMonitoring() },
            registerNomadHooks = ::registerNomadHooksIfConfigured,
            waitUntilClientIdIsAvailable = ::waitUntilClientIdIsAvailable,
            pendingProposalScheduler = { pendingProposalScheduler },
            apiMigrationManager = { apiMigrationManager },
            messageRepository = { messageRepository },
            callRepository = { callRepository },
            pushTokenUpdater = ::createPushTokenUpdater,
            mlsConversationsRecoveryManager = { mlsConversationsRecoveryManager },
            joinPendingMLSConversationsManager = { joinPendingMLSConversationsManager },
            conversationsRecoveryManager = { conversationsRecoveryManager },
            ephemeralMessageDeletionHandler = { kaliumUserGraph.messageEphemeralMessageDeletionHandler },
            observeE2EIConversationsVerificationStatuses = { observeE2EIConversationsVerificationStatuses },
            typingIndicatorSyncManager = { typingIndicatorSyncManager },
            mlsFaultyKeysConversationsRepair = { mlsFaultyKeysConversationsRepairUseCase },
            avsSyncStateReporter = { avsSyncStateReporter },
            confirmationDeliveryHandler = { kaliumUserGraph.messageConfirmationDeliveryHandler },
            pendingDeliveryWorker = {
                com.wire.kalium.logic.sync.deferred.MainAppPendingDeliveryWorker(
                    userStorage.database.pendingDeliveryConfirmationDAO,
                    conversationRepository,
                    kaliumUserGraph.messageSendDeliverSignalUseCase,
                    syncStateObserver.value,
                )
            },
            syncExecutor = { syncExecutor },
            localEventManager = { localEventManager },
            workScheduler = { userSessionWorkScheduler },
            callBackgroundManager = { callBackgroundManager },
        )
    }

    /** Platform creation starts app work before the prepared session is published. */
    internal fun startRuntime() {
        runtime.start()
    }

}

internal fun interface CachedClientIdClearer {
    operator fun invoke()
}
