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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.backup.BackupDataSource
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepositoryDataSource
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageDataSource
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.logout.LogoutDataSource
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.prekey.PreKeyDataSource
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCase
import com.wire.kalium.logic.feature.e2ei.ACMECertificatesSyncUseCaseImpl
import com.wire.kalium.logic.feature.featureConfig.SyncFeatureConfigsUseCase
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.AppsFeatureHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefiller
import com.wire.kalium.logic.feature.proteus.ProteusPreKeyRefillerImpl
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelectorImpl
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.AvsSyncStateReporterImpl
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.ObserveSyncStateUseCaseImpl
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCase
import com.wire.kalium.logic.sync.SendPendingMessagesUseCase
import com.wire.kalium.logic.sync.SendPendingMessagesUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.periodic.MeetingOccurrencesSyncWorker
import com.wire.kalium.logic.sync.periodic.MeetingOccurrencesSyncWorkerImpl
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorkerImpl
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiver
import com.wire.kalium.logic.sync.receiver.FeatureConfigEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiver
import com.wire.kalium.logic.sync.receiver.MeetingEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandlerImpl
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings7 {
    @Provides
    @UserSessionBinding("appsFeatureHandler")
    fun userSessionProvideAppsFeatureHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AppsFeatureHandler =
        AppsFeatureHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("secondFactorPasswordChallengeConfigHandler")
    fun userSessionProvideSecondFactorPasswordChallengeConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): SecondFactorPasswordChallengeConfigHandler =
        SecondFactorPasswordChallengeConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("selfDeletingMessagesConfigHandler")
    fun userSessionProvideSelfDeletingMessagesConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): SelfDeletingMessagesConfigHandler =
        SelfDeletingMessagesConfigHandler(userConfigRepository(), kaliumConfigs().selfDeletingMessages)

    @Provides
    @UserSessionBinding("e2eiConfigHandler")
    fun userSessionProvideE2eiConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): E2EIConfigHandler =
        E2EIConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("appLockConfigHandler")
    fun userSessionProvideAppLockConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AppLockConfigHandler =
        AppLockConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("allowedGlobalOperationsHandler")
    fun userSessionProvideAllowedGlobalOperationsHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AllowedGlobalOperationsHandler =
        AllowedGlobalOperationsHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("cellsConfigHandler")
    fun userSessionProvideCellsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): CellsConfigHandler =
        CellsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("enableUserProfileQRCodeConfigHandler")
    fun userSessionProvideEnableUserProfileQRCodeConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): EnableUserProfileQRCodeConfigHandler =
        EnableUserProfileQRCodeConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("assetAuditLogConfigHandler")
    fun userSessionProvideAssetAuditLogConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): AssetAuditLogConfigHandler =
        AssetAuditLogConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("preventAdminlessGroupsConfigHandler")
    fun userSessionProvidePreventAdminlessGroupsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): PreventAdminlessGroupsConfigHandler =
        PreventAdminlessGroupsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("meetingsConfigHandler")
    fun userSessionProvideMeetingsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
    ): MeetingsConfigHandler =
        MeetingsConfigHandler(userConfigRepository(), slowSyncRepository())

    @Provides
    @UserSessionBinding("featureConfigEventReceiver")
    fun userSessionProvideFeatureConfigEventReceiver(
        @UserSessionBinding("enableUserProfileQRCodeConfigHandler")
        enableUserProfileQRCodeConfigHandler: Provider<EnableUserProfileQRCodeConfigHandler>,
        @UserSessionBinding("preventAdminlessGroupsConfigHandler")
        preventAdminlessGroupsConfigHandler: Provider<PreventAdminlessGroupsConfigHandler>,
        @UserSessionBinding("selfDeletingMessagesConfigHandler")
        selfDeletingMessagesConfigHandler: Provider<SelfDeletingMessagesConfigHandler>,
        @UserSessionBinding("allowedGlobalOperationsHandler")
        allowedGlobalOperationsHandler: Provider<AllowedGlobalOperationsHandler>,
        @UserSessionBinding("classifiedDomainsConfigHandler")
        classifiedDomainsConfigHandler: Provider<ClassifiedDomainsConfigHandler>,
        @UserSessionBinding("conferenceCallingConfigHandler")
        conferenceCallingConfigHandler: Provider<ConferenceCallingConfigHandler>,
        @UserSessionBinding("assetAuditLogConfigHandler")
        assetAuditLogConfigHandler: Provider<AssetAuditLogConfigHandler>,
        @UserSessionBinding("mlsMigrationConfigHandler")
        mlsMigrationConfigHandler: Provider<MLSMigrationConfigHandler>,
        @UserSessionBinding("fileSharingConfigHandler")
        fileSharingConfigHandler: Provider<FileSharingConfigHandler>,
        @UserSessionBinding("guestRoomConfigHandler")
        guestRoomConfigHandler: Provider<GuestRoomConfigHandler>,
        @UserSessionBinding("meetingsConfigHandler")
        meetingsConfigHandler: Provider<MeetingsConfigHandler>,
        @UserSessionBinding("appLockConfigHandler")
        appLockConfigHandler: Provider<AppLockConfigHandler>,
        @UserSessionBinding("cellsConfigHandler")
        cellsConfigHandler: Provider<CellsConfigHandler>,
        @UserSessionBinding("e2eiConfigHandler")
        e2eiConfigHandler: Provider<E2EIConfigHandler>,
        @UserSessionBinding("mlsConfigHandler")
        mlsConfigHandler: Provider<MLSConfigHandler>,
    ): FeatureConfigEventReceiver =
        FeatureConfigEventReceiverImpl(
            guestRoomConfigHandler(),
            fileSharingConfigHandler(),
            mlsConfigHandler(),
            mlsMigrationConfigHandler(),
            classifiedDomainsConfigHandler(),
            conferenceCallingConfigHandler(),
            selfDeletingMessagesConfigHandler(),
            e2eiConfigHandler(),
            appLockConfigHandler(),
            allowedGlobalOperationsHandler(),
            cellsConfigHandler(),
            enableUserProfileQRCodeConfigHandler(),
            assetAuditLogConfigHandler(),
            preventAdminlessGroupsConfigHandler(),
            meetingsConfigHandler(),
        )

    @Provides
    @UserSessionBinding("meetingCreateEventHandler")
    fun userSessionProvideMeetingCreateEventHandler(
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
    ): MeetingCreateEventHandler =
        MeetingCreateEventHandlerImpl(
            meetingRepository = meetingRepository(),
        )

    @Provides
    @UserSessionBinding("meetingDeleteEventHandler")
    fun userSessionProvideMeetingDeleteEventHandler(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): MeetingDeleteEventHandler =
        MeetingDeleteEventHandlerImpl(
            meetingRepository = meetingRepository(),
            userRepository = userRepository(),
            notificationEventsManager = notificationEventsManager(),
        )

    @Provides
    @UserSessionBinding("meetingUpdateEventHandler")
    fun userSessionProvideMeetingUpdateEventHandler(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): MeetingUpdateEventHandler =
        MeetingUpdateEventHandlerImpl(
            meetingRepository = meetingRepository(),
            userRepository = userRepository(),
            notificationEventsManager = notificationEventsManager(),
        )

    @Provides
    @UserSessionBinding("meetingMemberAddEventHandler")
    fun userSessionProvideMeetingMemberAddEventHandler(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): MeetingMemberAddEventHandler =
        MeetingMemberAddEventHandlerImpl(
            meetingRepository = meetingRepository(),
            userRepository = userRepository(),
            notificationEventsManager = notificationEventsManager(),
        )

    @Provides
    @UserSessionBinding("meetingEventReceiver")
    fun userSessionProvideMeetingEventReceiver(
        @UserSessionBinding("meetingMemberAddEventHandler")
        meetingMemberAddEventHandler: Provider<MeetingMemberAddEventHandler>,
        @UserSessionBinding("meetingCreateEventHandler")
        meetingCreateEventHandler: Provider<MeetingCreateEventHandler>,
        @UserSessionBinding("meetingDeleteEventHandler")
        meetingDeleteEventHandler: Provider<MeetingDeleteEventHandler>,
        @UserSessionBinding("meetingUpdateEventHandler")
        meetingUpdateEventHandler: Provider<MeetingUpdateEventHandler>,
    ): MeetingEventReceiver =
        MeetingEventReceiverImpl(
            meetingCreateEventHandler = meetingCreateEventHandler(),
            meetingDeleteEventHandler = meetingDeleteEventHandler(),
            meetingUpdateEventHandler = meetingUpdateEventHandler(),
            meetingMemberAddEventHandler = meetingMemberAddEventHandler(),
        )

    @Provides
    @UserSessionBinding("preKeyRepository")
    fun userSessionProvidePreKeyRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("proteusClientProvider")
        proteusClientProvider: Provider<ProteusClientProvider>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PreKeyRepository =
        PreKeyDataSource(
            authenticatedNetworkContainer().preKeyApi,
            proteusClientProvider(),
            clientIdProvider(),
            userStorage().database.prekeyDAO,
            userStorage().database.clientDAO,
            userStorage().database.metadataDAO,
        )

    @Provides
    @UserSessionBinding("certificateRevocationListRepository")
    fun userSessionProvideCertificateRevocationListRepository(
        @UserSessionBinding("unboundNetworkContainer")
        unboundNetworkContainer: Provider<UnboundNetworkContainer>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CertificateRevocationListRepository =
        CertificateRevocationListRepositoryDataSource(
            acmeApi = unboundNetworkContainer().acmeApi,
            metadataDAO = userStorage().database.metadataDAO,
            userConfigRepository = userConfigRepository()
        )

    @Provides
    @UserSessionBinding("proteusPreKeyRefiller")
    fun userSessionProvideProteusPreKeyRefiller(
        @UserSessionBinding("preKeyRepository")
        preKeyRepository: Provider<PreKeyRepository>,
    ): ProteusPreKeyRefiller =
        ProteusPreKeyRefillerImpl(preKeyRepository())

    @Provides
    @UserSessionBinding("userConfigSyncWorker")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserConfigSyncWorker(
        @UserSessionBinding("acmeCertificatesSyncUseCase")
        acmeCertificatesSyncUseCase: Provider<ACMECertificatesSyncUseCase>,
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("syncFeatureConfigsUseCase")
        syncFeatureConfigsUseCase: Provider<SyncFeatureConfigsUseCase>,
        @UserSessionBinding("mlsPublicKeysRepository")
        mlsPublicKeysRepository: Provider<MLSPublicKeysRepository>,
        @UserSessionBinding("proteusPreKeyRefiller")
        proteusPreKeyRefiller: Provider<ProteusPreKeyRefiller>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): UserConfigSyncWorker =
        UserConfigSyncWorkerImpl(
            incrementalSyncRepository = incrementalSyncRepository(),
            syncFeatureConfigsUseCase = syncFeatureConfigsUseCase(),
            proteusPreKeyRefiller = proteusPreKeyRefiller(),
            mlsPublicKeysRepository = mlsPublicKeysRepository(),
            acmeCertificatesSyncUseCase = acmeCertificatesSyncUseCase(),
            kaliumLogger = userScopedLogger(),
        )

    @Provides
    @UserSessionBinding("sendPendingMessages")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSendPendingMessages(
        @LegacyMessageBinding("messageSender")
        messageMessageSender: Provider<MessageSender>,
        @LegacyMessageBinding("sendPendingAssetMessage")
        messageSendPendingAssetMessage: Provider<SendPendingAssetMessageUseCase>,
        @LegacyMessageBinding("messageSendFailureHandler")
        messageMessageSendFailureHandler: Provider<MessageSendFailureHandler>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SendPendingMessagesUseCase =
        SendPendingMessagesUseCaseImpl(
            messageRepository = messageRepository(),
            messageSender = messageMessageSender(),
            userId = userId(),
            sendPendingAssetMessage = messageSendPendingAssetMessage(),
            messageSendFailureHandler = messageMessageSendFailureHandler(),
        )

    @Provides
    @UserSessionBinding("meetingOccurrencesSyncWorker")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMeetingOccurrencesSyncWorker(
        @UserSessionBinding("meetingRepository")
        meetingRepository: Provider<MeetingRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): MeetingOccurrencesSyncWorker =
        MeetingOccurrencesSyncWorkerImpl(
            meetingRepository = meetingRepository(),
            featureSupport = featureSupport()
        )

    @Provides
    @UserSessionBinding("keyPackageRepository")
    fun userSessionProvideKeyPackageRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): KeyPackageRepository =
        KeyPackageDataSource(
            clientIdProvider(),
            authenticatedNetworkContainer().keyPackageApi,
            userId()
        )

    @Provides
    @UserSessionBinding("logoutRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLogoutRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): LogoutRepository =
        LogoutDataSource(
            authenticatedNetworkContainer().logoutApi,
            userStorage().database.metadataDAO,
            userStorage().database.pendingSubconversationProposalDAO,
        )

    @Provides
    @UserSessionBinding("backupRepository")
    fun userSessionProvideBackupRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): BackupRepository =
        BackupDataSource(
            selfUserId = userId(),
            userDAO = userStorage().database.userDAO,
            conversationDAO = userStorage().database.conversationDAO,
            messageDAO = userStorage().database.messageDAO,
            reactionDAO = userStorage().database.reactionDAO,
        )

    @Provides
    @UserSessionBinding("observeSyncState")
    fun userSessionProvideObserveSyncState(
        @UserSessionBinding("syncManager")
        syncManager: Provider<SyncManager>,
    ): ObserveSyncStateUseCase =
        ObserveSyncStateUseCaseImpl(syncManager())

    @Provides
    @UserSessionBinding("avsSyncStateReporter")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideAvsSyncStateReporter(
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): AvsSyncStateReporter =
        AvsSyncStateReporterImpl(
            callManager = callManager().value,
            incrementalSyncRepository = incrementalSyncRepository(),
            kaliumLogger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("protoContentMapper")
    fun userSessionProvideProtoContentMapper(
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ProtoContentMapper =
        ProtoContentMapperImpl(selfUserId = userId())

    @Provides
    @UserSessionBinding("oneOnOneProtocolSelector")
    fun userSessionProvideOneOnOneProtocolSelector(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): OneOnOneProtocolSelector =
        OneOnOneProtocolSelectorImpl(
            userRepository(),
            userConfigRepository()
        )

    @Provides
    @UserSessionBinding("acmeCertificatesSyncUseCase")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideAcmeCertificatesSyncUseCase(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("e2eiRepository")
        e2eiRepository: Provider<E2EIRepository>,
        @UserSessionBinding("isE2EIEnabled")
        isE2EIEnabled: Provider<IsE2EIEnabledUseCase>,
    ): ACMECertificatesSyncUseCase =
        ACMECertificatesSyncUseCaseImpl(
            e2eiRepository = e2eiRepository(),
            kaliumLogger = userScopedLogger(),
            isE2EIEnabledUseCase = isE2EIEnabled()
        )

    @Provides
    @UserSessionBinding("refreshUsersWithoutMetadata")
    fun userSessionProvideRefreshUsersWithoutMetadata(
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): RefreshUsersWithoutMetadataUseCase =
        RefreshUsersWithoutMetadataUseCaseImpl(
            userRepository()
        )

}
