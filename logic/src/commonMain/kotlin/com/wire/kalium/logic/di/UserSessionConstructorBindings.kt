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
import com.wire.kalium.logic.cache.MLSSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProviderImpl
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.cache.SelfConversationIdProviderImpl
import com.wire.kalium.logic.data.call.CallModerationActionsDataSource
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.call.InCallReactionsDataSource
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.VideoStateCheckerImpl
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.CryptoTransactionProviderImpl
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProviderImpl
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProviderImpl
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCaseImpl
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCaseImpl
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserverImpl
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProviderImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCase
import com.wire.kalium.logic.feature.connection.SyncConnectionsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManagerImpl
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManagerImpl
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCase
import com.wire.kalium.logic.feature.conversation.MigrateConversationToMLSUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsForUserUseCaseImpl
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCase
import com.wire.kalium.logic.feature.conversation.RecoverMLSConversationsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolver
import com.wire.kalium.logic.feature.conversation.mls.MLSOneOnOneConversationResolverImpl
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchConversationMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.handler.AppsFeatureHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConsumableNotificationsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
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
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCase
import com.wire.kalium.logic.feature.meeting.SyncMeetingsUseCaseImpl
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationWorkerImpl
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelectorImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCaseImpl
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCase
import com.wire.kalium.logic.feature.session.GetProxyCredentialsUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCase
import com.wire.kalium.logic.feature.user.GetDefaultProtocolUseCaseImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.IsMeetingsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.IsPreventAdminlessGroupsEnabledUseCase
import com.wire.kalium.logic.feature.user.IsPreventAdminlessGroupsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveIsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.ObserveIsMeetingsEnabledUseCaseImpl
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
import com.wire.kalium.logic.sync.SendPendingMessagesUseCase
import com.wire.kalium.logic.sync.SendPendingMessagesUseCaseImpl
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventGathererImpl
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessorImpl
import com.wire.kalium.logic.sync.incremental.IncrementalSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.periodic.MeetingOccurrencesSyncWorker
import com.wire.kalium.logic.sync.periodic.MeetingOccurrencesSyncWorkerImpl
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiver
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.PollVoteHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldSystemMessagesHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.logic.sync.slow.SlowSlowSyncCriteriaProviderImpl
import com.wire.kalium.logic.sync.slow.SlowSyncCriteriaProvider
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandler
import com.wire.kalium.logic.sync.slow.SlowSyncRecoveryHandlerImpl
import com.wire.kalium.logic.sync.slow.SyncNomadMessagesDuringSlowSyncUseCaseImpl
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Constructor-injected implementations.
 * Qualified cache adapters retain the original API cache only; implementations stay unscoped.
 */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionConstructorBindings {
    @Binds
    @UserSessionBinding("addSystemMessageToAllConversationsUseCase")
    val AddSystemMessageToAllConversationsUseCaseImpl.bind: AddSystemMessageToAllConversationsUseCase

    @Binds
    @UserSessionBinding("appLockTeamFeatureConfigObserver")
    val AppLockTeamFeatureConfigObserverImpl.bind: AppLockTeamFeatureConfigObserver

    @Binds
    @UserSessionBinding("applicationMessageHandlerLastReadContentHandlerImpl")
    val LastReadContentHandlerImpl.bind: LastReadContentHandlerImpl

    @Binds
    @UserSessionBinding("applicationMessageHandlerMessageCompositeEditHandlerImpl")
    val MessageCompositeEditHandlerImpl.bind: MessageCompositeEditHandlerImpl

    @Binds
    @UserSessionBinding("applicationMessageHandlerMessageMultipartEditHandlerImpl")
    val MessageMultipartEditHandlerImpl.bind: MessageMultipartEditHandlerImpl

    @Binds
    @UserSessionBinding("applicationMessageHandlerPollVoteHandlerImpl")
    val PollVoteHandlerImpl.bind: PollVoteHandlerImpl

    @Binds
    @UserSessionBinding("approveLegalHoldRequest")
    val ApproveLegalHoldRequestUseCaseImpl.bind: ApproveLegalHoldRequestUseCase

    @Binds
    @UserSessionBinding("appsFeatureHandler")
    val AppsFeatureHandler.bind: AppsFeatureHandler

    @Binds
    @UserSessionBinding("assetMessageHandler")
    val AssetMessageHandlerImpl.bind: AssetMessageHandler

    @Binds
    @UserSessionBinding("buttonActionConfirmationHandler")
    val ButtonActionConfirmationHandlerImpl.bind: ButtonActionConfirmationHandler

    @Provides
    @UserSessionBinding("buttonActionHandler")
    @SingleIn(UserLifetime::class)
    fun provideButtonActionHandler(impl: ButtonActionHandlerImpl): ButtonActionHandler = impl

    @Provides
    @UserSessionBinding("callModerationActionsRepository")
    @SingleIn(UserLifetime::class)
    fun provideCallModerationActionsRepository(impl: CallModerationActionsDataSource): CallModerationActionsRepository = impl

    @Binds
    @UserSessionBinding("channelAddPermissionUpdateEventHandler")
    val ChannelAddPermissionUpdateEventHandlerImpl.bind: ChannelAddPermissionUpdateEventHandler

    @Binds
    @UserSessionBinding("clearUserData")
    val ClearUserDataUseCaseImpl.bind: ClearUserDataUseCase

    @Binds
    @UserSessionBinding("consumableNotificationsConfigHandler")
    val ConsumableNotificationsConfigHandler.bind: ConsumableNotificationsConfigHandler

    @Provides
    @UserSessionBinding("conversationEventReceiver")
    @SingleIn(UserLifetime::class)
    fun provideConversationEventReceiver(impl: ConversationEventReceiverImpl): ConversationEventReceiver = impl

    @Provides
    @UserSessionBinding("conversationsRecoveryManager")
    @SingleIn(UserLifetime::class)
    fun provideConversationsRecoveryManager(impl: ConversationsRecoveryManagerImpl): ConversationsRecoveryManager = impl

    @Binds
    @UserSessionBinding("createAndPersistRecentlyEndedCallMetadata")
    val CreateAndPersistRecentlyEndedCallMetadataUseCaseImpl.bind: CreateAndPersistRecentlyEndedCallMetadataUseCase

    @Binds
    @UserSessionBinding("cryptoTransactionProvider")
    val CryptoTransactionProviderImpl.bind: CryptoTransactionProvider

    @Binds
    @UserSessionBinding("dataTransferEventHandler")
    val DataTransferEventHandlerImpl.bind: DataTransferEventHandler

    @Binds
    @UserSessionBinding("deleteConversationUseCase")
    val DeleteConversationUseCaseImpl.bind: DeleteConversationUseCase

    @Binds
    @UserSessionBinding("deletedConversationHandler")
    val DeletedConversationEventHandlerImpl.bind: DeletedConversationEventHandler

    @Binds
    @UserSessionBinding("eventGatherer")
    val EventGathererImpl.bind: EventGatherer

    @Provides
    @UserSessionBinding("eventProcessor")
    @SingleIn(UserLifetime::class)
    fun provideEventProcessor(impl: EventProcessorImpl): EventProcessor = impl

    @Binds
    @UserSessionBinding("featureConfigEventReceiver")
    val FeatureConfigEventReceiverImpl.bind: FeatureConfigEventReceiver

    @Binds
    @UserSessionBinding("fetchConversationIfUnknownUseCase")
    val FetchConversationIfUnknownUseCaseImpl.bind: FetchConversationIfUnknownUseCase

    @Binds
    @UserSessionBinding("fetchConversationMLSVerificationStatus")
    val FetchConversationMLSVerificationStatusUseCaseImpl.bind: FetchConversationMLSVerificationStatusUseCase

    @Binds
    @UserSessionBinding("fetchConversationUseCase")
    val FetchConversationUseCaseImpl.bind: FetchConversationUseCase

    @Binds
    @UserSessionBinding("fetchConversationsUseCase")
    val FetchConversationsUseCaseImpl.bind: FetchConversationsUseCase

    @Binds
    @UserSessionBinding("fetchLegalHoldForSelfUserFromRemoteUseCase")
    val FetchLegalHoldForSelfUserFromRemoteUseCaseImpl.bind: FetchLegalHoldForSelfUserFromRemoteUseCase

    @Provides
    @UserSessionBinding("fetchMLSVerificationStatusUseCase")
    @SingleIn(UserLifetime::class)
    fun provideFetchMLSVerificationStatusUseCase(impl: FetchMLSVerificationStatusUseCaseImpl): FetchMLSVerificationStatusUseCase = impl

    @Provides
    @UserSessionBinding("getCallConversationType")
    @SingleIn(UserLifetime::class)
    fun provideGetCallConversationType(impl: GetCallConversationTypeProviderImpl): GetCallConversationTypeProvider = impl

    @Binds
    @UserSessionBinding("getDefaultProtocol")
    val GetDefaultProtocolUseCaseImpl.bind: GetDefaultProtocolUseCase

    @Binds
    @UserSessionBinding("getPersistentWebSocketStatus")
    val GetPersistentWebSocketStatusImpl.bind: GetPersistentWebSocketStatus

    @Binds
    @UserSessionBinding("getProxyCredentials")
    val GetProxyCredentialsUseCaseImpl.bind: GetProxyCredentialsUseCase

    @Provides
    @UserSessionBinding("inCallReactionsRepository")
    @SingleIn(UserLifetime::class)
    fun provideInCallReactionsRepository(impl: InCallReactionsDataSource): InCallReactionsRepository = impl

    @Binds
    @UserSessionBinding("incrementalSyncRecoveryHandler")
    val IncrementalSyncRecoveryHandlerImpl.bind: IncrementalSyncRecoveryHandlerImpl

    @Provides
    @UserSessionBinding("incrementalSyncRepository")
    @SingleIn(UserLifetime::class)
    fun provideIncrementalSyncRepository(impl: InMemoryIncrementalSyncRepository): IncrementalSyncRepository = impl

    @Binds
    @UserSessionBinding("isAllowedToRegisterMLSClient")
    val IsAllowedToRegisterMLSClientUseCaseImpl.bind: IsAllowedToRegisterMLSClientUseCase

    @Binds
    @UserSessionBinding("isClientAsyncNotificationsCapableProvider")
    val IsClientAsyncNotificationsCapableProviderImpl.bind: IsClientAsyncNotificationsCapableProvider

    @Binds
    @UserSessionBinding("isE2EIEnabled")
    val IsE2EIEnabledUseCaseImpl.bind: IsE2EIEnabledUseCase

    @Binds
    @UserSessionBinding("isFileSharingEnabled")
    val IsFileSharingEnabledUseCaseImpl.bind: IsFileSharingEnabledUseCase

    @Binds
    @UserSessionBinding("isMLSEnabled")
    val IsMLSEnabledUseCaseImpl.bind: IsMLSEnabledUseCase

    @Binds
    @UserSessionBinding("isMeetingsEnabled")
    val IsMeetingsEnabledUseCaseImpl.bind: IsMeetingsEnabledUseCase

    @Binds
    @UserSessionBinding("isMessageSentInSelfConversation")
    val IsMessageSentInSelfConversationUseCaseImpl.bind: IsMessageSentInSelfConversationUseCase

    @Binds
    @UserSessionBinding("isPreventAdminlessGroupsEnabled")
    val IsPreventAdminlessGroupsEnabledUseCaseImpl.bind: IsPreventAdminlessGroupsEnabledUseCase

    @Provides
    @UserSessionBinding("joinPendingMLSConversationsManager")
    @SingleIn(UserLifetime::class)
    fun provideJoinPendingMLSConversationsManager(impl: JoinPendingMLSConversationsManagerImpl): JoinPendingMLSConversationsManager = impl

    @Binds
    @UserSessionBinding("keyPackageLimitsProvider")
    val KeyPackageLimitsProviderImpl.bind: KeyPackageLimitsProvider

    @Provides
    @UserSessionBinding("legalHoldRequestHandler")
    @SingleIn(UserLifetime::class)
    fun provideLegalHoldRequestHandler(impl: LegalHoldRequestHandlerImpl): LegalHoldRequestHandlerImpl = impl

    @Provides
    @UserSessionBinding("legalHoldSystemMessagesHandler")
    @SingleIn(UserLifetime::class)
    fun provideLegalHoldSystemMessagesHandler(impl: LegalHoldSystemMessagesHandlerImpl): LegalHoldSystemMessagesHandlerImpl = impl

    @Binds
    @UserSessionBinding("markE2EIRequiredAsNotified")
    val MarkEnablingE2EIAsNotifiedUseCaseImpl.bind: MarkEnablingE2EIAsNotifiedUseCase

    @Binds
    @UserSessionBinding("markGuestLinkFeatureFlagAsNotChanged")
    val MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl.bind: MarkGuestLinkFeatureFlagAsNotChangedUseCase

    @Binds
    @UserSessionBinding("markLegalHoldChangeAsNotifiedForSelf")
    val MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl.bind: MarkLegalHoldChangeAsNotifiedForSelfUseCase

    @Provides
    @UserSessionBinding("markNotifyForRevokedCertificateAsNotified")
    @SingleIn(UserLifetime::class)
    fun provideMarkNotifyForRevokedCertificateAsNotified(
        impl: MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl,
    ): MarkNotifyForRevokedCertificateAsNotifiedUseCase = impl

    @Binds
    @UserSessionBinding("markSelfDeletingMessagesAsNotified")
    val MarkSelfDeletionStatusAsNotifiedUseCaseImpl.bind: MarkSelfDeletionStatusAsNotifiedUseCase

    @Binds
    @UserSessionBinding("markTeamAppLockStatusAsNotified")
    val MarkTeamAppLockStatusAsNotifiedUseCaseImpl.bind: MarkTeamAppLockStatusAsNotifiedUseCase

    @Binds
    @UserSessionBinding("meetingCreateEventHandler")
    val MeetingCreateEventHandlerImpl.bind: MeetingCreateEventHandler

    @Binds
    @UserSessionBinding("meetingDeleteEventHandler")
    val MeetingDeleteEventHandlerImpl.bind: MeetingDeleteEventHandler

    @Binds
    @UserSessionBinding("meetingEventReceiver")
    val MeetingEventReceiverImpl.bind: MeetingEventReceiver

    @Binds
    @UserSessionBinding("meetingMemberAddEventHandler")
    val MeetingMemberAddEventHandlerImpl.bind: MeetingMemberAddEventHandler

    @Provides
    @UserSessionBinding("meetingOccurrencesSyncWorker")
    @SingleIn(UserLifetime::class)
    fun provideMeetingOccurrencesSyncWorker(impl: MeetingOccurrencesSyncWorkerImpl): MeetingOccurrencesSyncWorker = impl

    @Binds
    @UserSessionBinding("meetingUpdateEventHandler")
    val MeetingUpdateEventHandlerImpl.bind: MeetingUpdateEventHandler

    @Binds
    @UserSessionBinding("memberChangeHandler")
    val MemberChangeEventHandlerImpl.bind: MemberChangeEventHandler

    @Binds
    @UserSessionBinding("membersHavingLegalHoldClient")
    val MembersHavingLegalHoldClientUseCaseImpl.bind: MembersHavingLegalHoldClientUseCase

    @Binds
    @UserSessionBinding("migrateConversationToMLS")
    val MigrateConversationToMLSUseCaseImpl.bind: MigrateConversationToMLSUseCase

    @Binds
    @UserSessionBinding("mlsClientManagerRegisterMLSClientUseCaseImpl")
    val RegisterMLSClientUseCaseImpl.bindMlsClientManagerRegisterMLSClientUseCaseImpl: RegisterMLSClientUseCaseImpl

    @Binds
    @UserSessionBinding("mlsConfigHandler")
    val MLSConfigHandler.bind: MLSConfigHandler

    @Provides
    @UserSessionBinding("mlsConversationsRecoveryManager")
    @SingleIn(UserLifetime::class)
    fun provideMlsConversationsRecoveryManager(impl: MLSConversationsRecoveryManagerImpl): MLSConversationsRecoveryManager = impl

    @Binds
    @UserSessionBinding("mlsMigrationConfigHandler")
    val MLSMigrationConfigHandler.bind: MLSMigrationConfigHandler

    @Binds
    @UserSessionBinding("mlsMigrationWorker")
    val MLSMigrationWorkerImpl.bind: MLSMigrationWorkerImpl

    @Binds
    @UserSessionBinding("mlsOneOnOneConversationResolver")
    val MLSOneOnOneConversationResolverImpl.bind: MLSOneOnOneConversationResolver

    @Binds
    @UserSessionBinding("mlsResetConversationEventHandler")
    val MLSResetConversationEventHandlerImpl.bind: MLSResetConversationEventHandler

    @Provides
    @UserSessionBinding("mlsSelfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun provideMLSSelfConversationIdProvider(impl: MLSSelfConversationIdProviderImpl): MLSSelfConversationIdProvider = impl

    @Binds
    @UserSessionBinding("mlsWelcomeHandler")
    val MLSWelcomeEventHandlerImpl.bind: MLSWelcomeEventHandler

    @Binds
    @UserSessionBinding("newConversationHandler")
    val NewConversationEventHandlerImpl.bind: NewConversationEventHandler

    @Binds
    @UserSessionBinding("newUpdateConversationClientsForCurrentCall")
    val UpdateConversationClientsForCurrentCallUseCaseImpl.bind: UpdateConversationClientsForCurrentCallUseCaseImpl

    @Provides
    @UserSessionBinding("observeE2EIConversationsVerificationStatuses")
    @SingleIn(UserLifetime::class)
    fun provideObserveE2EIConversationsVerificationStatuses(
        impl: ObserveE2EIConversationsVerificationStatusesUseCaseImpl,
    ): ObserveE2EIConversationsVerificationStatusesUseCase = impl

    @Binds
    @UserSessionBinding("observeFileSharingStatus")
    val ObserveFileSharingStatusUseCaseImpl.bind: ObserveFileSharingStatusUseCase

    @Binds
    @UserSessionBinding("observeGuestRoomLinkFeatureFlag")
    val ObserveGuestRoomLinkFeatureFlagUseCaseImpl.bind: ObserveGuestRoomLinkFeatureFlagUseCase

    @Binds
    @UserSessionBinding("observeIsMeetingsEnabled")
    val ObserveIsMeetingsEnabledUseCaseImpl.bind: ObserveIsMeetingsEnabledUseCase

    @Binds
    @UserSessionBinding("observeLegalHoldChangeNotifiedForSelf")
    val ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl.bind: ObserveLegalHoldChangeNotifiedForSelfUseCase

    @Binds
    @UserSessionBinding("observeLegalHoldForSelfUser")
    val ObserveLegalHoldStateForSelfUserUseCaseImpl.bind: ObserveLegalHoldStateForSelfUserUseCase

    @Binds
    @UserSessionBinding("observeLegalHoldRequest")
    val ObserveLegalHoldRequestUseCaseImpl.bind: ObserveLegalHoldRequestUseCase

    @Binds
    @UserSessionBinding("observeLegalHoldStateForUser")
    val ObserveLegalHoldStateForUserUseCaseImpl.bind: ObserveLegalHoldStateForUserUseCase

    @Binds
    @UserSessionBinding("observeScreenshotCensoringConfig")
    val ObserveScreenshotCensoringConfigUseCaseImpl.bind: ObserveScreenshotCensoringConfigUseCase

    @Binds
    @UserSessionBinding("observeSecurityClassificationLabel")
    val ObserveSecurityClassificationLabelUseCaseImpl.bind: ObserveSecurityClassificationLabelUseCase

    @Provides
    @UserSessionBinding("observeShouldNotifyForRevokedCertificate")
    @SingleIn(UserLifetime::class)
    fun provideObserveShouldNotifyForRevokedCertificate(
        impl: ObserveShouldNotifyForRevokedCertificateUseCaseImpl,
    ): ObserveShouldNotifyForRevokedCertificateUseCase = impl

    @Binds
    @UserSessionBinding("observeTeamSettingsSelfDeletionStatus")
    val ObserveTeamSettingsSelfDeletingStatusUseCaseImpl.bind: ObserveTeamSettingsSelfDeletingStatusUseCase

    @Binds
    @UserSessionBinding("oneOnOneProtocolSelector")
    val OneOnOneProtocolSelectorImpl.bind: OneOnOneProtocolSelector

    @Binds
    @UserSessionBinding("persistConversationUseCase")
    val PersistConversationUseCaseImpl.bind: PersistConversationUseCase

    @Binds
    @UserSessionBinding("persistMessage")
    val PersistMessageUseCaseImpl.bind: PersistMessageUseCase

    @Binds
    @UserSessionBinding("persistPersistentWebSocketConnectionStatus")
    val PersistPersistentWebSocketConnectionStatusUseCaseImpl.bind: PersistPersistentWebSocketConnectionStatusUseCase

    @Binds
    @UserSessionBinding("persistScreenshotCensoringConfig")
    val PersistScreenshotCensoringConfigUseCaseImpl.bind: PersistScreenshotCensoringConfigUseCase

    @Provides
    @UserSessionBinding("proteusSelfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun provideProteusSelfConversationIdProvider(impl: ProteusSelfConversationIdProviderImpl): ProteusSelfConversationIdProvider = impl

    @Binds
    @UserSessionBinding("recoverMLSConversationsForUser")
    val RecoverMLSConversationsForUserUseCaseImpl.bind: RecoverMLSConversationsForUserUseCase

    @Binds
    @UserSessionBinding("recoverMLSConversationsUseCase")
    val RecoverMLSConversationsUseCaseImpl.bind: RecoverMLSConversationsUseCase

    @Binds
    @UserSessionBinding("registerMLSClientUseCase")
    val RegisterMLSClientUseCaseImpl.bindRegisterMLSClientUseCase: RegisterMLSClientUseCase

    @Binds
    @UserSessionBinding("resetMlsConversation")
    val ResetMLSConversationUseCaseImpl.bind: ResetMLSConversationUseCase

    @Binds
    @UserSessionBinding("restartSlowSyncProcessForRecoveryUseCase")
    val RestartSlowSyncProcessForRecoveryUseCaseImpl.bind: RestartSlowSyncProcessForRecoveryUseCase

    @Binds
    @UserSessionBinding("secondFactorPasswordChallengeConfigHandler")
    val SecondFactorPasswordChallengeConfigHandler.bind: SecondFactorPasswordChallengeConfigHandler

    @Provides
    @UserSessionBinding("selfConversationIdProvider")
    @SingleIn(UserLifetime::class)
    fun provideSelfConversationIdProvider(impl: SelfConversationIdProviderImpl): SelfConversationIdProvider = impl

    @Provides
    @UserSessionBinding("sendPendingMessages")
    @SingleIn(UserLifetime::class)
    fun provideSendPendingMessages(impl: SendPendingMessagesUseCaseImpl): SendPendingMessagesUseCase = impl

    @Binds
    @UserSessionBinding("slowSyncCriteriaProvider")
    val SlowSlowSyncCriteriaProviderImpl.bind: SlowSyncCriteriaProvider

    @Binds
    @UserSessionBinding("slowSyncRecoveryHandler")
    val SlowSyncRecoveryHandlerImpl.bind: SlowSyncRecoveryHandler

    @Binds
    @UserSessionBinding("slowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl")
    val SyncNomadMessagesDuringSlowSyncUseCaseImpl.bind: SyncNomadMessagesDuringSlowSyncUseCaseImpl

    @Binds
    @UserSessionBinding("syncConnections")
    val SyncConnectionsUseCaseImpl.bind: SyncConnectionsUseCase

    @Binds
    @UserSessionBinding("syncContacts")
    val SyncContactsUseCaseImpl.bind: SyncContactsUseCase

    @Binds
    @UserSessionBinding("syncFeatureConfigsUseCase")
    val SyncFeatureConfigsUseCaseImpl.bind: SyncFeatureConfigsUseCase

    @Binds
    @UserSessionBinding("syncMeetingsUseCase")
    val SyncMeetingsUseCaseImpl.bind: SyncMeetingsUseCase

    @Binds
    @UserSessionBinding("syncSelfUser")
    val SyncSelfUserUseCaseImpl.bind: SyncSelfUserUseCase

    @Binds
    @UserSessionBinding("syncStateObserverSyncStateObserverImpl")
    val SyncStateObserverImpl.bind: SyncStateObserverImpl

    @Binds
    @UserSessionBinding("syncUserProperties")
    val SyncUserPropertiesUseCaseImpl.bind: SyncUserPropertiesUseCase

    @Binds
    @UserSessionBinding("systemMessageInserter")
    val SystemMessageInserterImpl.bind: SystemMessageInserterImpl

    @Binds
    @UserSessionBinding("teamEventReceiver")
    val TeamEventReceiverImpl.bind: TeamEventReceiver

    @Binds
    @UserSessionBinding("updateConversationProtocolUseCase")
    val UpdateConversationProtocolUseCaseImpl.bind: UpdateConversationProtocolUseCase

    @Binds
    @UserSessionBinding("updateSupportedProtocols")
    val UpdateSelfUserSupportedProtocolsUseCaseImpl.bind: UpdateSelfUserSupportedProtocolsUseCase

    @Binds
    @UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    val UpdateSupportedProtocolsAndResolveOneOnOnesUseCaseImpl.bind: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase

    @Binds
    @UserSessionBinding("upgradeCurrentSessionUseCase")
    val UpgradeCurrentSessionUseCaseImpl.bind: UpgradeCurrentSessionUseCaseImpl

    @Binds
    @UserSessionBinding("userPropertiesEventReceiver")
    val UserPropertiesEventReceiverImpl.bind: UserPropertiesEventReceiver

    @Binds
    @UserSessionBinding("videoStateChecker")
    val VideoStateCheckerImpl.bind: VideoStateChecker

    @Provides
    @UserSessionBinding("x509CredentialAcquisitionConfigProvider")
    @SingleIn(UserLifetime::class)
    fun provideX509CredentialAcquisitionConfigProvider(
        impl: X509CredentialAcquisitionConfigProviderImpl,
    ): X509CredentialAcquisitionConfigProvider = impl

    @Binds
    @UserSessionBinding("messageRepositoryFactory")
    val UserSessionMessageRepositoryFactory.bind: UserSessionMessageRepositoryFactory

    @Binds
    @UserSessionBinding("incrementalWorkerFactory")
    val UserSessionIncrementalWorkerFactory.bind: UserSessionIncrementalWorkerFactory
}
