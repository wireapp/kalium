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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.logic.cache.MLSSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.notification.NotificationTokenDataSource
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
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
import com.wire.kalium.logic.data.client.MLSTransportProvider
import com.wire.kalium.logic.data.client.ProteusMigrationRecoveryHandler
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
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
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
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
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.session.token.AccessTokenRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.analytics.AnalyticsIdentifierManager
import com.wire.kalium.logic.feature.analytics.GetAnalyticsContactsDataUseCase
import com.wire.kalium.logic.feature.analytics.GetCurrentAnalyticsTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.analytics.ObserveAnalyticsTrackingIdentifierStatusUseCase
import com.wire.kalium.logic.feature.analytics.SetNewUserTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.LegacyCallsApi
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCaseImpl
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneMigrator
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.e2ei.CheckCrlRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
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
import com.wire.kalium.logic.feature.legalhold.ApproveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.FetchLegalHoldForSelfUserFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.MarkLegalHoldChangeAsNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldChangeNotifiedForSelfUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldRequestUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForSelfUserUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCase
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresherFactory
import com.wire.kalium.logic.feature.team.SyncSelfTeamUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.IsPreventAdminlessGroupsEnabledUseCase
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
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.SendPendingMessagesUseCase
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.SyncExecutorImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorker
import com.wire.kalium.logic.sync.local.LocalEventManagerImpl
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
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpacker
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldSystemMessagesHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandler
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncWorker
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi

@Suppress("LargeClass") // One typed entrypoint for each existing root property.
internal interface UserSessionApi {
    @get:UserSessionBinding("callMapper")
    val userSessionCallMapper: CallMapper

    @get:UserSessionBinding("qualifiedIdMapper")
    val userSessionQualifiedIdMapper: QualifiedIdMapper

    @get:UserSessionBinding("federatedIdMapper")
    val userSessionFederatedIdMapper: FederatedIdMapper

    @get:UserSessionBinding("isClientAsyncNotificationsCapableProvider")
    val userSessionIsClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider

    @get:UserSessionBinding("mlsSelfConversationIdProvider")
    val userSessionMlsSelfConversationIdProvider: MLSSelfConversationIdProvider

    @get:UserSessionBinding("proteusSelfConversationIdProvider")
    val userSessionProteusSelfConversationIdProvider: ProteusSelfConversationIdProvider

    @get:UserSessionBinding("selfConversationIdProvider")
    val userSessionSelfConversationIdProvider: SelfConversationIdProvider

    @get:UserSessionBinding("accessTokenRepository")
    val userSessionAccessTokenRepository: AccessTokenRepository

    @get:UserSessionBinding("accessTokenRefresherFactory")
    val userSessionAccessTokenRefresherFactory: AccessTokenRefresherFactory

    @get:UserSessionBinding("accessTokenRefresher")
    val userSessionAccessTokenRefresher: AccessTokenRefresher

    @get:UserSessionBinding("featureSupport")
    val userSessionFeatureSupport: FeatureSupport

    @get:UserSessionBinding("userScopedNomadHookFactory")
    val userSessionUserScopedNomadHookFactory: UserScopedNomadHookFactory

    @get:UserSessionBinding("userConfigRepository")
    val userSessionUserConfigRepository: UserConfigRepository

    @get:UserSessionBinding("userPropertyRepository")
    val userSessionUserPropertyRepository: UserPropertyRepository

    @get:UserSessionBinding("keyPackageLimitsProvider")
    val userSessionKeyPackageLimitsProvider: KeyPackageLimitsProvider

    @get:UserSessionBinding("proteusMigrationRecoveryHandler")
    val userSessionProteusMigrationRecoveryHandler: ProteusMigrationRecoveryHandler

    @get:UserSessionBinding("mlsTransportProvider")
    val userSessionMlsTransportProvider: MLSTransportProvider

    @get:UserSessionBinding("mlsConversationRepository")
    val userSessionMlsConversationRepository: MLSConversationRepository

    @get:UserSessionBinding("mlsMissingUsersRejectionHandlerProvider")
    val userSessionMlsMissingUsersRejectionHandlerProvider: UserSessionValue<() -> MLSMissingUsersMessageRejectionHandler>

    @get:UserSessionBinding("e2eiRepository")
    val userSessionE2eiRepository: E2EIRepository

    @get:UserSessionBinding("x509CredentialAcquisitionConfigProvider")
    val userSessionX509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider

    @get:UserSessionBinding("notificationTokenRepository")
    val userSessionNotificationTokenRepository: NotificationTokenDataSource

    @get:UserSessionBinding("subconversationRepository")
    val userSessionSubconversationRepository: SubconversationRepositoryImpl

    @get:UserSessionBinding("conversationRepository")
    val userSessionConversationRepository: ConversationRepository

    @get:UserSessionBinding("conversationMetaDataRepository")
    val userSessionConversationMetaDataRepository: ConversationMetaDataRepository

    @get:UserSessionBinding("conversationFolderRepository")
    val userSessionConversationFolderRepository: ConversationFolderRepository

    @get:UserSessionBinding("conversationGroupRepository")
    val userSessionConversationGroupRepository: ConversationGroupRepository

    @get:UserSessionBinding("newConversationMembersRepository")
    val userSessionNewConversationMembersRepository: NewConversationMembersRepository

    @get:UserSessionBinding("messageRepository")
    val userSessionMessageRepository: MessageRepository

    @get:UserSessionBinding("messageApiPagingCoordinator")
    val userSessionMessageApiPagingCoordinator: NomadMessagePagingCoordinator?

    @get:UserSessionBinding("nomadMessagePagingCoordinator")
    val userSessionNomadMessagePagingCoordinator: NomadMessagePagingCoordinator?

    @get:UserSessionBinding("messageMetadataRepository")
    val userSessionMessageMetadataRepository: MessageMetadataRepository

    @get:UserSessionBinding("compositeMessageRepository")
    val userSessionCompositeMessageRepository: CompositeMessageRepository

    @get:UserSessionBinding("pollMessageRepository")
    val userSessionPollMessageRepository: PollMessageRepository

    @get:UserSessionBinding("messageDraftRepository")
    val userSessionMessageDraftRepository: MessageDraftRepository

    @get:UserSessionBinding("slowSyncRepository")
    val userSessionSlowSyncRepository: SlowSyncRepository

    @get:UserSessionBinding("incrementalSyncRepository")
    val userSessionIncrementalSyncRepository: IncrementalSyncRepository

    @get:UserSessionBinding("legalHoldSystemMessagesHandler")
    val userSessionLegalHoldSystemMessagesHandler: LegalHoldSystemMessagesHandlerImpl

    @get:UserSessionBinding("legalHoldHandler")
    val userSessionLegalHoldHandler: LegalHoldHandlerImpl

    @get:UserSessionBinding("userRepository")
    val userSessionUserRepository: UserRepository

    @get:UserSessionBinding("accountRepository")
    val userSessionAccountRepository: AccountRepository

    @get:UserSessionBinding("pushTokenRepository")
    val userSessionPushTokenRepository: PushTokenRepository

    @get:UserSessionBinding("teamRepository")
    val userSessionTeamRepository: TeamRepository

    @get:UserSessionBinding("serviceRepository")
    val userSessionServiceRepository: ServiceRepository

    @get:UserSessionBinding("appRepository")
    val userSessionAppRepository: AppRepository

    @get:UserSessionBinding("persistConversationsUseCase")
    val userSessionPersistConversationsUseCase: PersistConversationsUseCase

    @get:UserSessionBinding("persistConversationUseCase")
    val userSessionPersistConversationUseCase: PersistConversationUseCase

    @get:UserSessionBinding("fetchMLSOneToOneConversationUseCase")
    val userSessionFetchMLSOneToOneConversationUseCase: FetchMLSOneToOneConversationUseCase

    @get:UserSessionBinding("fetchConversationUseCase")
    val userSessionFetchConversationUseCase: FetchConversationUseCase

    @get:UserSessionBinding("fetchConversationIfUnknownUseCase")
    val userSessionFetchConversationIfUnknownUseCase: FetchConversationIfUnknownUseCase

    @get:UserSessionBinding("fetchConversationsUseCase")
    val userSessionFetchConversationsUseCase: FetchConversationsUseCase

    @get:UserSessionBinding("connectionRepository")
    val userSessionConnectionRepository: ConnectionRepository

    @get:UserSessionBinding("userSearchApiWrapper")
    val userSessionUserSearchApiWrapper: UserSearchApiWrapper

    @get:UserSessionBinding("searchUserRepository")
    val userSessionSearchUserRepository: SearchUserRepository

    @get:UserSessionBinding("cryptoStateBackupRemoteRepository")
    val userSessionCryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository

    @get:UserSessionBinding("persistMessage")
    val userSessionPersistMessage: PersistMessageUseCase

    @get:UserSessionBinding("addSystemMessageToAllConversationsUseCase")
    val userSessionAddSystemMessageToAllConversationsUseCase: AddSystemMessageToAllConversationsUseCase

    @get:UserSessionBinding("restartSlowSyncProcessForRecoveryUseCase")
    val userSessionRestartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase

    @get:UserSessionBinding("callRepository")
    val userSessionCallRepository: CallRepository

    @get:UserSessionBinding("clientRemoteRepository")
    val userSessionClientRemoteRepository: ClientRemoteRepository

    @get:UserSessionBinding("clientRegistrationStorage")
    val userSessionClientRegistrationStorage: ClientRegistrationStorage

    @get:UserSessionBinding("clientRepository")
    val userSessionClientRepository: ClientRepository

    @get:UserSessionBinding("messageSendingScheduler")
    val userSessionMessageSendingScheduler: MessageSendingScheduler

    @get:UserSessionBinding("audioNormalizedLoudnessScheduler")
    val userSessionAudioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler

    @get:UserSessionBinding("assetRepository")
    val userSessionAssetRepository: AssetRepository

    @get:UserSessionBinding("eventGatherer")
    val userSessionEventGatherer: EventGatherer

    @get:UserSessionBinding("cryptoTransactionProvider")
    val userSessionCryptoTransactionProvider: CryptoTransactionProvider

    @get:UserSessionBinding("eventProcessor")
    val userSessionEventProcessor: EventProcessor

    @get:UserSessionBinding("slowSyncCriteriaProvider")
    val userSessionSlowSyncCriteriaProvider: SlowSyncCriteriaProvider

    @get:UserSessionBinding("syncManager")
    val userSessionSyncManager: SyncManager

    @get:UserSessionBinding("syncStateObserver")
    val userSessionSyncStateObserver: UserSessionValue<Lazy<SyncStateObserver>>

    @get:UserSessionBinding("syncExecutorImpl")
    val userSessionSyncExecutorImpl: SyncExecutorImpl

    @get:UserSessionBinding("syncExecutor")
    val userSessionSyncExecutor: SyncExecutor

    @get:UserSessionBinding("syncConversations")
    val userSessionSyncConversations: SyncConversationsUseCase

    @get:UserSessionBinding("updateConversationProtocolUseCase")
    val userSessionUpdateConversationProtocolUseCase: UpdateConversationProtocolUseCase

    @get:UserSessionBinding("syncConnections")
    val userSessionSyncConnections: SyncConnectionsUseCase

    @get:UserSessionBinding("syncSelfUser")
    val userSessionSyncSelfUser: SyncSelfUserUseCase

    @get:UserSessionBinding("syncUserProperties")
    val userSessionSyncUserProperties: SyncUserPropertiesUseCase

    @get:UserSessionBinding("syncContacts")
    val userSessionSyncContacts: SyncContactsUseCase

    @get:UserSessionBinding("syncSelfTeamUseCase")
    val userSessionSyncSelfTeamUseCase: SyncSelfTeamUseCase

    @get:UserSessionBinding("joinExistingMLSConversationUseCase")
    val userSessionJoinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase

    @get:UserSessionBinding("registerMLSClientUseCase")
    val userSessionRegisterMLSClientUseCase: RegisterMLSClientUseCase

    @get:UserSessionBinding("recoverMLSConversationsUseCase")
    val userSessionRecoverMLSConversationsUseCase: RecoverMLSConversationsUseCase

    @get:UserSessionBinding("joinExistingMLSConversations")
    val userSessionJoinExistingMLSConversations: JoinExistingMLSConversationsUseCase

    @get:UserSessionBinding("recoverMLSConversationsForUser")
    val userSessionRecoverMLSConversationsForUser: RecoverMLSConversationsForUserUseCase

    @get:UserSessionBinding("joinSubconversationUseCase")
    val userSessionJoinSubconversationUseCase: JoinSubconversationUseCase

    @get:UserSessionBinding("leaveSubconversationUseCase")
    val userSessionLeaveSubconversationUseCase: LeaveSubconversationUseCase

    @get:UserSessionBinding("mlsOneOnOneConversationResolver")
    val userSessionMlsOneOnOneConversationResolver: MLSOneOnOneConversationResolver

    @get:UserSessionBinding("oneOnOneMigrator")
    val userSessionOneOnOneMigrator: OneOnOneMigrator

    @get:UserSessionBinding("pendingActionsRepository")
    val userSessionPendingActionsRepository: PendingActionsRepository

    @get:UserSessionBinding("oneOnOneResolver")
    val userSessionOneOnOneResolver: OneOnOneResolver

    @get:UserSessionBinding("recoverPendingOneOnOneResolutionsUseCase")
    val userSessionRecoverPendingOneOnOneResolutionsUseCase: RecoverPendingOneOnOneResolutionsUseCase

    @get:UserSessionBinding("recoverPendingMLSGroupJoinsUseCase")
    val userSessionRecoverPendingMLSGroupJoinsUseCase: RecoverPendingMLSGroupJoinsUseCase

    @get:UserSessionBinding("updateSupportedProtocols")
    val userSessionUpdateSupportedProtocols: UpdateSelfUserSupportedProtocolsUseCase

    @get:UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    val userSessionUpdateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase

    @get:UserSessionBinding("slowSyncWorker")
    val userSessionSlowSyncWorker: SlowSyncWorker

    @get:UserSessionBinding("slowSyncRecoveryHandler")
    val userSessionSlowSyncRecoveryHandler: SlowSyncRecoveryHandler

    @get:UserSessionBinding("syncMigrationStepsProvider")
    val userSessionSyncMigrationStepsProvider: UserSessionValue<() -> SyncMigrationStepsProvider>

    @get:UserSessionBinding("slowSyncManager")
    val userSessionSlowSyncManager: SlowSyncManager

    @get:UserSessionBinding("mlsConversationsRecoveryManager")
    val userSessionMlsConversationsRecoveryManager: MLSConversationsRecoveryManager

    @get:UserSessionBinding("joinPendingMLSConversationsManager")
    val userSessionJoinPendingMLSConversationsManager: JoinPendingMLSConversationsManager

    @get:UserSessionBinding("mlsFaultyKeysConversationsRepairUseCase")
    val userSessionMlsFaultyKeysConversationsRepairUseCase: MLSFaultyKeysConversationsRepairUseCaseImpl

    @get:UserSessionBinding("conversationsRecoveryManager")
    val userSessionConversationsRecoveryManager: ConversationsRecoveryManager

    @get:UserSessionBinding("incrementalSyncWorker")
    val userSessionIncrementalSyncWorker: IncrementalSyncWorker

    @get:UserSessionBinding("incrementalSyncRecoveryHandler")
    val userSessionIncrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandlerImpl

    @get:UserSessionBinding("incrementalSyncManager")
    val userSessionIncrementalSyncManager: IncrementalSyncManager

    @get:UserSessionBinding("localEventManager")
    val userSessionLocalEventManager: LocalEventManagerImpl

    @get:UserSessionBinding("upgradeCurrentSessionUseCase")
    val userSessionUpgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCaseImpl

    @get:UserSessionBinding("sessionRefreshSuggestedEventHandler")
    val userSessionSessionRefreshSuggestedEventHandler: SessionRefreshSuggestedEventHandler

    @get:UserSessionBinding("apiMigrations")
    val userSessionApiMigrations: List<Pair<Int, ApiMigrationV3>>

    @get:UserSessionBinding("apiMigrationManager")
    val userSessionApiMigrationManager: ApiMigrationManager

    @get:UserSessionBinding("eventRepository")
    val userSessionEventRepository: EventRepository

    @get:UserSessionBinding("mlsMigrator")
    val userSessionMlsMigrator: MLSMigrator

    @get:UserSessionBinding("keyPackageManager")
    val userSessionKeyPackageManager: KeyPackageManager

    @get:UserSessionBinding("keyingMaterialsManager")
    val userSessionKeyingMaterialsManager: KeyingMaterialsManager

    @get:UserSessionBinding("mlsClientManager")
    val userSessionMlsClientManager: MLSClientManager

    @get:UserSessionBinding("mlsMigrationWorker")
    val userSessionMlsMigrationWorker: MLSMigrationWorkerImpl

    @get:UserSessionBinding("mlsMigrationManager")
    val userSessionMlsMigrationManager: MLSMigrationManager

    @get:UserSessionBinding("mlsPublicKeysRepository")
    val userSessionMlsPublicKeysRepository: MLSPublicKeysRepository

    @get:UserSessionBinding("videoStateChecker")
    val userSessionVideoStateChecker: VideoStateChecker

    @get:UserSessionBinding("pendingSubconversationProposals")
    val userSessionPendingSubconversationProposals: PendingSubconversationProposals

    @get:UserSessionBinding("pendingProposalScheduler")
    val userSessionPendingProposalScheduler: PendingProposalScheduler

    @get:UserSessionBinding("endCallOnMLSReset")
    val userSessionEndCallOnMLSReset: EndCallOnMLSResetUseCase

    @get:UserSessionBinding("callBackgroundManager")
    val userSessionCallBackgroundManager: CallBackgroundManager

    @get:UserSessionBinding("conversationClientsInCallUpdater")
    val userSessionConversationClientsInCallUpdater: ConversationClientsInCallUpdater

    @get:UserSessionBinding("epochInfoUpdater")
    val userSessionEpochInfoUpdater: EpochInfoUpdater

    @get:UserSessionBinding("getCallConversationType")
    val userSessionGetCallConversationType: GetCallConversationTypeProvider

    @get:UserSessionBinding("updateConversationClientsForCurrentCall")
    val userSessionUpdateConversationClientsForCurrentCall: UserSessionValue<Lazy<UpdateConversationClientsForCurrentCallUseCase>>

    @get:UserSessionBinding("reactionRepository")
    val userSessionReactionRepository: ReactionRepositoryImpl

    @get:UserSessionBinding("receiptRepository")
    val userSessionReceiptRepository: ReceiptRepositoryImpl

    @get:UserSessionBinding("persistReaction")
    val userSessionPersistReaction: PersistReactionUseCase

    @get:UserSessionBinding("mlsUnpacker")
    val userSessionMlsUnpacker: MLSMessageUnpacker

    @get:UserSessionBinding("proteusUnpacker")
    val userSessionProteusUnpacker: ProteusMessageUnpacker

    @get:UserSessionBinding("messageEncoder")
    val userSessionMessageEncoder: MessageContentEncoder

    @get:UserSessionBinding("systemMessageInserter")
    val userSessionSystemMessageInserter: SystemMessageInserterImpl

    @get:UserSessionBinding("receiptMessageHandler")
    val userSessionReceiptMessageHandler: ReceiptMessageHandlerImpl

    @get:UserSessionBinding("isMessageSentInSelfConversation")
    val userSessionIsMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase

    @get:UserSessionBinding("assetMessageHandler")
    val userSessionAssetMessageHandler: AssetMessageHandler

    @get:UserSessionBinding("buttonActionConfirmationHandler")
    val userSessionButtonActionConfirmationHandler: ButtonActionConfirmationHandler

    @get:UserSessionBinding("dataTransferEventHandler")
    val userSessionDataTransferEventHandler: DataTransferEventHandler

    @get:UserSessionBinding("inCallReactionsRepository")
    val userSessionInCallReactionsRepository: InCallReactionsRepository

    @get:UserSessionBinding("callModerationActionsRepository")
    val userSessionCallModerationActionsRepository: CallModerationActionsRepository

    @get:UserSessionBinding("buttonActionHandler")
    val userSessionButtonActionHandler: ButtonActionHandler

    @get:UserSessionBinding("callingMessageHandler")
    val userSessionCallingMessageHandler: CallingMessageHandler

    @get:UserSessionBinding("applicationMessageHandler")
    val userSessionApplicationMessageHandler: ApplicationMessageHandler

    @get:UserSessionBinding("linkPreviewsResolver")
    val userSessionLinkPreviewsResolver: LinkPreviewImagesResolver

    @get:UserSessionBinding("staleEpochVerifier")
    val userSessionStaleEpochVerifier: StaleEpochVerifier

    @get:UserSessionBinding("newMessageHandler")
    val userSessionNewMessageHandler: NewMessageEventHandler

    @get:UserSessionBinding("newGroupConversationSystemMessagesCreator")
    val userSessionNewGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator

    @get:UserSessionBinding("newConversationHandler")
    val userSessionNewConversationHandler: NewConversationEventHandler

    @get:UserSessionBinding("deletedConversationHandler")
    val userSessionDeletedConversationHandler: DeletedConversationEventHandler

    @get:UserSessionBinding("deleteConversationReminderEventHandler")
    val userSessionDeleteConversationReminderEventHandler: DeleteConversationReminderEventHandler

    @get:UserSessionBinding("memberJoinHandler")
    val userSessionMemberJoinHandler: MemberJoinEventHandler

    @get:UserSessionBinding("memberLeaveHandler")
    val userSessionMemberLeaveHandler: MemberLeaveEventHandler

    @get:UserSessionBinding("memberChangeHandler")
    val userSessionMemberChangeHandler: MemberChangeEventHandler

    @get:UserSessionBinding("mlsWelcomeHandler")
    val userSessionMlsWelcomeHandler: MLSWelcomeEventHandler

    @get:UserSessionBinding("renamedConversationHandler")
    val userSessionRenamedConversationHandler: RenamedConversationEventHandler

    @get:UserSessionBinding("receiptModeUpdateEventHandler")
    val userSessionReceiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler

    @get:UserSessionBinding("conversationMessageTimerEventHandler")
    val userSessionConversationMessageTimerEventHandler: ConversationMessageTimerEventHandler

    @get:UserSessionBinding("conversationCodeUpdateHandler")
    val userSessionConversationCodeUpdateHandler: CodeUpdatedHandler

    @get:UserSessionBinding("conversationCodeDeletedHandler")
    val userSessionConversationCodeDeletedHandler: CodeDeletedHandler

    @get:UserSessionBinding("typingIndicatorHandler")
    val userSessionTypingIndicatorHandler: TypingIndicatorHandler

    @get:UserSessionBinding("protocolUpdateEventHandler")
    val userSessionProtocolUpdateEventHandler: ProtocolUpdateEventHandler

    @get:UserSessionBinding("channelAddPermissionUpdateEventHandler")
    val userSessionChannelAddPermissionUpdateEventHandler: ChannelAddPermissionUpdateEventHandler

    @get:UserSessionBinding("conversationAccessUpdateEventHandler")
    val userSessionConversationAccessUpdateEventHandler: AccessUpdateEventHandler

    @get:UserSessionBinding("mlsResetConversationEventHandler")
    val userSessionMlsResetConversationEventHandler: MLSResetConversationEventHandler

    @get:UserSessionBinding("conversationEventReceiver")
    val userSessionConversationEventReceiver: ConversationEventReceiver

    @get:UserSessionBinding("legalHoldRequestHandler")
    val userSessionLegalHoldRequestHandler: LegalHoldRequestHandlerImpl

    @get:UserSessionBinding("observeLegalHoldStateForUser")
    val userSessionObserveLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase

    @get:UserSessionBinding("observeAnalyticsTrackingIdentifierStatus")
    val userSessionObserveAnalyticsTrackingIdentifierStatus: ObserveAnalyticsTrackingIdentifierStatusUseCase

    @get:UserSessionBinding("setNewUserTrackingIdentifier")
    val userSessionSetNewUserTrackingIdentifier: SetNewUserTrackingIdentifierUseCase

    @get:UserSessionBinding("getCurrentAnalyticsTrackingIdentifier")
    val userSessionGetCurrentAnalyticsTrackingIdentifier: GetCurrentAnalyticsTrackingIdentifierUseCase

    @get:UserSessionBinding("analyticsIdentifierManager")
    val userSessionAnalyticsIdentifierManager: AnalyticsIdentifierManager

    @get:UserSessionBinding("observeLegalHoldForSelfUser")
    val userSessionObserveLegalHoldForSelfUser: ObserveLegalHoldStateForSelfUserUseCase

    @get:UserSessionBinding("observeLegalHoldChangeNotifiedForSelf")
    val userSessionObserveLegalHoldChangeNotifiedForSelf: ObserveLegalHoldChangeNotifiedForSelfUseCase

    @get:UserSessionBinding("markLegalHoldChangeAsNotifiedForSelf")
    val userSessionMarkLegalHoldChangeAsNotifiedForSelf: MarkLegalHoldChangeAsNotifiedForSelfUseCase

    @get:UserSessionBinding("observeLegalHoldRequest")
    val userSessionObserveLegalHoldRequest: ObserveLegalHoldRequestUseCase

    @get:UserSessionBinding("approveLegalHoldRequest")
    val userSessionApproveLegalHoldRequest: ApproveLegalHoldRequestUseCase

    @get:UserSessionBinding("fetchUsersClientsFromRemote")
    val userSessionFetchUsersClientsFromRemote: FetchUsersClientsFromRemoteUseCase

    @get:UserSessionBinding("membersHavingLegalHoldClient")
    val userSessionMembersHavingLegalHoldClient: MembersHavingLegalHoldClientUseCase

    @get:UserSessionBinding("fetchLegalHoldForSelfUserFromRemoteUseCase")
    val userSessionFetchLegalHoldForSelfUserFromRemoteUseCase: FetchLegalHoldForSelfUserFromRemoteUseCase

    @get:UserSessionBinding("userEventReceiver")
    val userSessionUserEventReceiver: UserEventReceiver

    @get:UserSessionBinding("userPropertiesEventReceiver")
    val userSessionUserPropertiesEventReceiver: UserPropertiesEventReceiver

    @get:UserSessionBinding("federationEventReceiver")
    val userSessionFederationEventReceiver: FederationEventReceiver

    @get:UserSessionBinding("teamEventReceiver")
    val userSessionTeamEventReceiver: TeamEventReceiver

    @get:UserSessionBinding("guestRoomConfigHandler")
    val userSessionGuestRoomConfigHandler: GuestRoomConfigHandler

    @get:UserSessionBinding("fileSharingConfigHandler")
    val userSessionFileSharingConfigHandler: FileSharingConfigHandler

    @get:UserSessionBinding("mlsConfigHandler")
    val userSessionMlsConfigHandler: MLSConfigHandler

    @get:UserSessionBinding("mlsMigrationConfigHandler")
    val userSessionMlsMigrationConfigHandler: MLSMigrationConfigHandler

    @get:UserSessionBinding("classifiedDomainsConfigHandler")
    val userSessionClassifiedDomainsConfigHandler: ClassifiedDomainsConfigHandler

    @get:UserSessionBinding("conferenceCallingConfigHandler")
    val userSessionConferenceCallingConfigHandler: ConferenceCallingConfigHandler

    @get:UserSessionBinding("consumableNotificationsConfigHandler")
    val userSessionConsumableNotificationsConfigHandler: ConsumableNotificationsConfigHandler

    @get:UserSessionBinding("appsFeatureHandler")
    val userSessionAppsFeatureHandler: AppsFeatureHandler

    @get:UserSessionBinding("secondFactorPasswordChallengeConfigHandler")
    val userSessionSecondFactorPasswordChallengeConfigHandler: SecondFactorPasswordChallengeConfigHandler

    @get:UserSessionBinding("selfDeletingMessagesConfigHandler")
    val userSessionSelfDeletingMessagesConfigHandler: SelfDeletingMessagesConfigHandler

    @get:UserSessionBinding("e2eiConfigHandler")
    val userSessionE2eiConfigHandler: E2EIConfigHandler

    @get:UserSessionBinding("appLockConfigHandler")
    val userSessionAppLockConfigHandler: AppLockConfigHandler

    @get:UserSessionBinding("allowedGlobalOperationsHandler")
    val userSessionAllowedGlobalOperationsHandler: AllowedGlobalOperationsHandler

    @get:UserSessionBinding("cellsConfigHandler")
    val userSessionCellsConfigHandler: CellsConfigHandler

    @get:UserSessionBinding("enableUserProfileQRCodeConfigHandler")
    val userSessionEnableUserProfileQRCodeConfigHandler: EnableUserProfileQRCodeConfigHandler

    @get:UserSessionBinding("assetAuditLogConfigHandler")
    val userSessionAssetAuditLogConfigHandler: AssetAuditLogConfigHandler

    @get:UserSessionBinding("preventAdminlessGroupsConfigHandler")
    val userSessionPreventAdminlessGroupsConfigHandler: PreventAdminlessGroupsConfigHandler

    @get:UserSessionBinding("meetingsConfigHandler")
    val userSessionMeetingsConfigHandler: MeetingsConfigHandler

    @get:UserSessionBinding("featureConfigEventReceiver")
    val userSessionFeatureConfigEventReceiver: FeatureConfigEventReceiver

    @get:UserSessionBinding("meetingCreateEventHandler")
    val userSessionMeetingCreateEventHandler: MeetingCreateEventHandler

    @get:UserSessionBinding("meetingDeleteEventHandler")
    val userSessionMeetingDeleteEventHandler: MeetingDeleteEventHandler

    @get:UserSessionBinding("meetingUpdateEventHandler")
    val userSessionMeetingUpdateEventHandler: MeetingUpdateEventHandler

    @get:UserSessionBinding("meetingMemberAddEventHandler")
    val userSessionMeetingMemberAddEventHandler: MeetingMemberAddEventHandler

    @get:UserSessionBinding("meetingEventReceiver")
    val userSessionMeetingEventReceiver: MeetingEventReceiver

    @get:UserSessionBinding("preKeyRepository")
    val userSessionPreKeyRepository: PreKeyRepository

    @get:UserSessionBinding("certificateRevocationListRepository")
    val userSessionCertificateRevocationListRepository: CertificateRevocationListRepository

    @get:UserSessionBinding("proteusPreKeyRefiller")
    val userSessionProteusPreKeyRefiller: ProteusPreKeyRefiller

    @get:UserSessionBinding("userConfigSyncWorker")
    val userSessionUserConfigSyncWorker: UserConfigSyncWorker

    @get:UserSessionBinding("sendPendingMessages")
    val userSessionSendPendingMessages: SendPendingMessagesUseCase

    @get:UserSessionBinding("meetingOccurrencesSyncWorker")
    val userSessionMeetingOccurrencesSyncWorker: MeetingOccurrencesSyncWorker

    @get:UserSessionBinding("keyPackageRepository")
    val userSessionKeyPackageRepository: KeyPackageRepository

    @get:UserSessionBinding("logoutRepository")
    val userSessionLogoutRepository: LogoutRepository

    @get:UserSessionBinding("backupRepository")
    val userSessionBackupRepository: BackupRepository

    @get:UserSessionBinding("observeSyncState")
    val userSessionObserveSyncState: ObserveSyncStateUseCase

    @get:UserSessionBinding("avsSyncStateReporter")
    val userSessionAvsSyncStateReporter: AvsSyncStateReporter

    @get:UserSessionBinding("protoContentMapper")
    val userSessionProtoContentMapper: ProtoContentMapper

    @get:UserSessionBinding("oneOnOneProtocolSelector")
    val userSessionOneOnOneProtocolSelector: OneOnOneProtocolSelector

    @get:UserSessionBinding("acmeCertificatesSyncUseCase")
    val userSessionAcmeCertificatesSyncUseCase: ACMECertificatesSyncUseCase

    @get:UserSessionBinding("refreshUsersWithoutMetadata")
    val userSessionRefreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase

    @get:UserSessionBinding("clearUserData")
    val userSessionClearUserData: ClearUserDataUseCase

    @get:UserSessionBinding("validateAssetMimeType")
    val userSessionValidateAssetMimeType: ValidateAssetFileTypeUseCase

    @get:UserSessionBinding("logout")
    val userSessionLogout: LogoutUseCase

    @get:UserSessionBinding("persistPersistentWebSocketConnectionStatus")
    val userSessionPersistPersistentWebSocketConnectionStatus: PersistPersistentWebSocketConnectionStatusUseCase

    @get:UserSessionBinding("getPersistentWebSocketStatus")
    val userSessionGetPersistentWebSocketStatus: GetPersistentWebSocketStatus

    @get:UserSessionBinding("featureConfigRepository")
    val userSessionFeatureConfigRepository: FeatureConfigRepository

    @get:UserSessionBinding("isFileSharingEnabled")
    val userSessionIsFileSharingEnabled: IsFileSharingEnabledUseCase

    @get:UserSessionBinding("isPreventAdminlessGroupsEnabled")
    val userSessionIsPreventAdminlessGroupsEnabled: IsPreventAdminlessGroupsEnabledUseCase

    @get:UserSessionBinding("isMeetingsEnabled")
    val userSessionIsMeetingsEnabled: IsMeetingsEnabledUseCase

    @get:UserSessionBinding("observeIsMeetingsEnabled")
    val userSessionObserveIsMeetingsEnabled: ObserveIsMeetingsEnabledUseCase

    @get:UserSessionBinding("observeFileSharingStatus")
    val userSessionObserveFileSharingStatus: ObserveFileSharingStatusUseCase

    @get:UserSessionBinding("observeShouldNotifyForRevokedCertificate")
    val userSessionObserveShouldNotifyForRevokedCertificate: ObserveShouldNotifyForRevokedCertificateUseCase

    @get:UserSessionBinding("markNotifyForRevokedCertificateAsNotified")
    val userSessionMarkNotifyForRevokedCertificateAsNotified: MarkNotifyForRevokedCertificateAsNotifiedUseCase

    @get:UserSessionBinding("markGuestLinkFeatureFlagAsNotChanged")
    val userSessionMarkGuestLinkFeatureFlagAsNotChanged: MarkGuestLinkFeatureFlagAsNotChangedUseCase

    @get:UserSessionBinding("appLockTeamFeatureConfigObserver")
    val userSessionAppLockTeamFeatureConfigObserver: AppLockTeamFeatureConfigObserver

    @get:UserSessionBinding("markTeamAppLockStatusAsNotified")
    val userSessionMarkTeamAppLockStatusAsNotified: MarkTeamAppLockStatusAsNotifiedUseCase

    @get:UserSessionBinding("markSelfDeletingMessagesAsNotified")
    val userSessionMarkSelfDeletingMessagesAsNotified: MarkSelfDeletionStatusAsNotifiedUseCase

    @get:UserSessionBinding("observeSelfDeletingMessages")
    val userSessionObserveSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase

    @get:UserSessionBinding("observeTeamSettingsSelfDeletionStatus")
    val userSessionObserveTeamSettingsSelfDeletionStatus: ObserveTeamSettingsSelfDeletingStatusUseCase

    @get:UserSessionBinding("persistNewSelfDeletionStatus")
    val userSessionPersistNewSelfDeletionStatus: PersistNewSelfDeletionTimerUseCase

    @get:UserSessionBinding("observeGuestRoomLinkFeatureFlag")
    val userSessionObserveGuestRoomLinkFeatureFlag: ObserveGuestRoomLinkFeatureFlagUseCase

    @get:UserSessionBinding("markFileSharingStatusAsNotified")
    val userSessionMarkFileSharingStatusAsNotified: MarkFileSharingChangeAsNotifiedUseCase

    @get:UserSessionBinding("isMLSEnabled")
    val userSessionIsMLSEnabled: IsMLSEnabledUseCase

    @get:UserSessionBinding("isE2EIEnabled")
    val userSessionIsE2EIEnabled: IsE2EIEnabledUseCase

    @get:UserSessionBinding("getDefaultProtocol")
    val userSessionGetDefaultProtocol: GetDefaultProtocolUseCase

    @get:UserSessionBinding("observeE2EIRequired")
    val userSessionObserveE2EIRequired: ObserveE2EIRequiredUseCase

    @get:UserSessionBinding("markE2EIRequiredAsNotified")
    val userSessionMarkE2EIRequiredAsNotified: MarkEnablingE2EIAsNotifiedUseCase

    @get:UserSessionBinding("isAllowedToRegisterMLSClient")
    val userSessionIsAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase

    @get:UserSessionBinding("syncFeatureConfigsUseCase")
    val userSessionSyncFeatureConfigsUseCase: SyncFeatureConfigsUseCase

    @get:UserSessionBinding("observeSecurityClassificationLabel")
    val userSessionObserveSecurityClassificationLabel: ObserveSecurityClassificationLabelUseCase

    @get:UserSessionBinding("getOtherUserSecurityClassificationLabel")
    val userSessionGetOtherUserSecurityClassificationLabel: ObserveOtherUserSecurityClassificationLabelUseCase

    @get:UserSessionBinding("persistScreenshotCensoringConfig")
    val userSessionPersistScreenshotCensoringConfig: PersistScreenshotCensoringConfigUseCase

    @get:UserSessionBinding("observeScreenshotCensoringConfig")
    val userSessionObserveScreenshotCensoringConfig: ObserveScreenshotCensoringConfigUseCase

    @get:UserSessionBinding("fetchConversationMLSVerificationStatus")
    val userSessionFetchConversationMLSVerificationStatus: FetchConversationMLSVerificationStatusUseCase

    @get:UserSessionBinding("kaliumFileSystem")
    val userSessionKaliumFileSystem: KaliumFileSystem

    @get:UserSessionBinding("checkCrlRevocationList")
    val userSessionCheckCrlRevocationList: CheckCrlRevocationListUseCase

    @get:UserSessionBinding("createAndPersistRecentlyEndedCallMetadata")
    val userSessionCreateAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase

    @get:UserSessionBinding("migrateFromPersonalToTeam")
    val userSessionMigrateFromPersonalToTeam: MigrateFromPersonalToTeamUseCase

    @get:UserSessionBinding("getProxyCredentials")
    val userSessionGetProxyCredentials: GetProxyCredentialsUseCase

    @get:UserSessionBinding("fetchMLSVerificationStatusUseCase")
    val userSessionFetchMLSVerificationStatusUseCase: FetchMLSVerificationStatusUseCase

    @get:UserSessionBinding("observeE2EIConversationsVerificationStatuses")
    val userSessionObserveE2EIConversationsVerificationStatuses: ObserveE2EIConversationsVerificationStatusesUseCase

    @get:UserSessionBinding("typingIndicatorSyncManager")
    val userSessionTypingIndicatorSyncManager: TypingIndicatorSyncManager

    @get:UserSessionBinding("analyticsRepository")
    val userSessionAnalyticsRepository: AnalyticsRepository

    @get:UserSessionBinding("getTeamUrlUseCase")
    val userSessionGetTeamUrlUseCase: GetTeamUrlUseCase

    @get:UserSessionBinding("getAnalyticsContactsData")
    val userSessionGetAnalyticsContactsData: GetAnalyticsContactsDataUseCase

    @get:UserSessionBinding("deleteConversationUseCase")
    val userSessionDeleteConversationUseCase: DeleteConversationUseCase

    @get:UserSessionBinding("resetMlsConversation")
    val userSessionResetMlsConversation: ResetMLSConversationUseCase

    @get:UserSessionBinding("migrateConversationToMLS")
    val userSessionMigrateConversationToMLS: MigrateConversationToMLSUseCase

    @get:UserSessionBinding("meetingRepository")
    val userSessionMeetingRepository: MeetingRepository

    @get:UserSessionBinding("syncMeetingsUseCase")
    val userSessionSyncMeetingsUseCase: SyncMeetingsUseCase

    @get:UserSessionBinding("messageRepositoryFactory")
    val userSessionMessageRepositoryFactory: UserSessionMessageRepositoryFactory

    @get:UserSessionBinding("callSignalJournal")
    val userSessionCallSignalJournal: CallSignalJournal

    @get:UserSessionBinding("incrementalWorkerFactory")
    val userSessionIncrementalWorkerFactory: UserSessionIncrementalWorkerFactory

    @get:UserSessionBinding("freshCallsOperation")
    val userSessionFreshCallsOperation: LegacyCallsApi
    @get:UserSessionBinding("pushTokenUpdater")
    val userSessionPushTokenUpdater: PushTokenUpdater
}
