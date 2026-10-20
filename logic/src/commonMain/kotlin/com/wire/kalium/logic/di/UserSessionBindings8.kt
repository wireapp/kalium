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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigDataSource
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserver
import com.wire.kalium.logic.feature.applock.AppLockTeamFeatureConfigObserverImpl
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.applock.MarkTeamAppLockStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCaseImpl
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.channels.ChannelsFeatureConfigurationHandler
import com.wire.kalium.logic.feature.channels.LegacyChannelsBinding
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCase
import com.wire.kalium.logic.feature.client.IsAllowedToRegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.client.LegacyClientBinding
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.ObserveConversationMembersUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveOtherUserSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCase
import com.wire.kalium.logic.feature.conversation.ObserveSecurityClassificationLabelUseCaseImpl
import com.wire.kalium.logic.feature.e2ei.usecase.GetMLSClientIdentityUseCase
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
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveTeamSettingsSelfDeletingStatusUseCaseImpl
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCase
import com.wire.kalium.logic.feature.selfDeletingMessages.PersistNewSelfDeletionTimerUseCaseImpl
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
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
import com.wire.kalium.logic.feature.user.LegacyUserBinding
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.MarkFileSharingChangeAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkSelfDeletionStatusAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCaseImpl
import com.wire.kalium.logic.feature.user.ObserveIsMeetingsEnabledUseCase
import com.wire.kalium.logic.feature.user.ObserveIsMeetingsEnabledUseCaseImpl
import com.wire.kalium.logic.feature.user.e2ei.MarkNotifyForRevokedCertificateAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.e2ei.MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.user.e2ei.ObserveShouldNotifyForRevokedCertificateUseCase
import com.wire.kalium.logic.feature.user.e2ei.ObserveShouldNotifyForRevokedCertificateUseCaseImpl
import com.wire.kalium.logic.feature.user.guestroomlink.MarkGuestLinkFeatureFlagAsNotChangedUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl
import com.wire.kalium.logic.feature.user.guestroomlink.ObserveGuestRoomLinkFeatureFlagUseCase
import com.wire.kalium.logic.feature.user.guestroomlink.ObserveGuestRoomLinkFeatureFlagUseCaseImpl
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCase
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatus
import com.wire.kalium.logic.feature.user.webSocketStatus.GetPersistentWebSocketStatusImpl
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings8 {
    @Provides
    @UserSessionBinding("clearUserData")
    fun userSessionProvideClearUserData(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ClearUserDataUseCase =
        ClearUserDataUseCaseImpl(userStorage())

    @Provides
    @UserSessionBinding("validateAssetMimeType")
    fun userSessionProvideValidateAssetMimeType(): ValidateAssetFileTypeUseCase =
        ValidateAssetFileTypeUseCaseImpl()

    @Provides
    @UserSessionBinding("logout")
    fun userSessionProvideLogout(
        @LegacyClientBinding("deregisterNativePushToken")
        clientDeregisterNativePushToken: Provider<DeregisterTokenUseCase>,
        @LegacyClientBinding("clearClientData")
        clientClearClientData: Provider<ClearClientDataUseCase>,
        @UserSessionBinding("freshEstablishedCalls")
        freshEstablishedCalls: Provider<ObserveEstablishedCallsUseCase>,
        @UserSessionBinding("freshEndCall")
        freshEndCall: Provider<EndCallUseCase>,
        @UserSessionBinding("userSessionScopeProvider")
        userSessionScopeProvider: Provider<UserSessionScopeProvider>,
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("globalCoroutineScope")
        globalCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("pushTokenRepository")
        pushTokenRepository: Provider<PushTokenRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("logoutRepository")
        logoutRepository: Provider<LogoutRepository>,
        @UserSessionBinding("nomadServiceUrl")
        nomadServiceUrl: Provider<String?>,
        @UserSessionBinding("logoutCallback")
        logoutCallback: Provider<LogoutCallback>,
        @UserSessionBinding("clearUserData")
        clearUserData: Provider<ClearUserDataUseCase>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LogoutUseCase =
        LogoutUseCaseImpl(
            logoutRepository = logoutRepository(),
            sessionScope = sessionCoroutineScope(),
            sessionRepository = globalSessionRepository(),
            clientRepository = clientRepository(),
            userConfigRepository = userConfigRepository(),
            userId = userId(),
            deregisterTokenUseCase = clientDeregisterNativePushToken(),
            clearClientDataUseCase = clientClearClientData(),
            clearUserDataUseCase = clearUserData(),
            userSessionScopeProvider = userSessionScopeProvider(),
            pushTokenRepository = pushTokenRepository(),
            globalCoroutineScope = globalCoroutineScope(),
            userSessionWorkScheduler = userSessionWorkScheduler(),
            getEstablishedCallsUseCase = freshEstablishedCalls(),
            endCallUseCase = freshEndCall(),
            logoutCallback = logoutCallback(),
            kaliumConfigs = kaliumConfigs(),
            isNomadEnabled = { nomadServiceUrl()?.isNotBlank() == true },
        )

    @Provides
    @UserSessionBinding("persistPersistentWebSocketConnectionStatus")
    fun userSessionProvidePersistPersistentWebSocketConnectionStatus(
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistPersistentWebSocketConnectionStatusUseCase =
        PersistPersistentWebSocketConnectionStatusUseCaseImpl(userId(), globalSessionRepository())

    @Provides
    @UserSessionBinding("getPersistentWebSocketStatus")
    fun userSessionProvideGetPersistentWebSocketStatus(
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): GetPersistentWebSocketStatus =
        GetPersistentWebSocketStatusImpl(userId(), globalSessionRepository())

    @Provides
    @UserSessionBinding("featureConfigRepository")
    fun userSessionProvideFeatureConfigRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): FeatureConfigRepository =
        FeatureConfigDataSource(
            featureConfigApi = authenticatedNetworkContainer().featureConfigApi
        )

    @Provides
    @UserSessionBinding("isFileSharingEnabled")
    fun userSessionProvideIsFileSharingEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): IsFileSharingEnabledUseCase =
        IsFileSharingEnabledUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("isPreventAdminlessGroupsEnabled")
    fun userSessionProvideIsPreventAdminlessGroupsEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): IsPreventAdminlessGroupsEnabledUseCase =
        IsPreventAdminlessGroupsEnabledUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("isMeetingsEnabled")
    fun userSessionProvideIsMeetingsEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): IsMeetingsEnabledUseCase =
        IsMeetingsEnabledUseCaseImpl(userConfigRepository(), featureSupport())

    @Provides
    @UserSessionBinding("observeIsMeetingsEnabled")
    fun userSessionProvideObserveIsMeetingsEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): ObserveIsMeetingsEnabledUseCase =
        ObserveIsMeetingsEnabledUseCaseImpl(userConfigRepository(), featureSupport())

    @Provides
    @UserSessionBinding("observeFileSharingStatus")
    fun userSessionProvideObserveFileSharingStatus(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveFileSharingStatusUseCase =
        ObserveFileSharingStatusUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("observeShouldNotifyForRevokedCertificate")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideObserveShouldNotifyForRevokedCertificate(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveShouldNotifyForRevokedCertificateUseCase =
        ObserveShouldNotifyForRevokedCertificateUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("markNotifyForRevokedCertificateAsNotified")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMarkNotifyForRevokedCertificateAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkNotifyForRevokedCertificateAsNotifiedUseCase =
        MarkNotifyForRevokedCertificateAsNotifiedUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("markGuestLinkFeatureFlagAsNotChanged")
    fun userSessionProvideMarkGuestLinkFeatureFlagAsNotChanged(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkGuestLinkFeatureFlagAsNotChangedUseCase =
        MarkGuestLinkFeatureFlagAsNotChangedUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("appLockTeamFeatureConfigObserver")
    fun userSessionProvideAppLockTeamFeatureConfigObserver(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("nomadServiceUrl")
        nomadServiceUrl: Provider<String?>,
    ): AppLockTeamFeatureConfigObserver =
        AppLockTeamFeatureConfigObserverImpl(
            userConfigRepository = userConfigRepository(),
            nomadServiceUrl = nomadServiceUrl(),
        )

    @Provides
    @UserSessionBinding("markTeamAppLockStatusAsNotified")
    fun userSessionProvideMarkTeamAppLockStatusAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkTeamAppLockStatusAsNotifiedUseCase =
        MarkTeamAppLockStatusAsNotifiedUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("markSelfDeletingMessagesAsNotified")
    fun userSessionProvideMarkSelfDeletingMessagesAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkSelfDeletionStatusAsNotifiedUseCase =
        MarkSelfDeletionStatusAsNotifiedUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("observeSelfDeletingMessages")
    fun userSessionProvideObserveSelfDeletingMessages(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveSelfDeletionTimerSettingsForConversationUseCase =
        ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository(), conversationRepository())

    @Provides
    @UserSessionBinding("observeTeamSettingsSelfDeletionStatus")
    fun userSessionProvideObserveTeamSettingsSelfDeletionStatus(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveTeamSettingsSelfDeletingStatusUseCase =
        ObserveTeamSettingsSelfDeletingStatusUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("persistNewSelfDeletionStatus")
    fun userSessionProvidePersistNewSelfDeletionStatus(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): PersistNewSelfDeletionTimerUseCase =
        PersistNewSelfDeletionTimerUseCaseImpl(conversationRepository())

    @Provides
    @UserSessionBinding("observeGuestRoomLinkFeatureFlag")
    fun userSessionProvideObserveGuestRoomLinkFeatureFlag(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveGuestRoomLinkFeatureFlagUseCase =
        ObserveGuestRoomLinkFeatureFlagUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("markFileSharingStatusAsNotified")
    fun userSessionProvideMarkFileSharingStatusAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkFileSharingChangeAsNotifiedUseCase =
        MarkFileSharingChangeAsNotifiedUseCase(userConfigRepository())

    @Provides
    @UserSessionBinding("isMLSEnabled")
    fun userSessionProvideIsMLSEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): IsMLSEnabledUseCase =
        IsMLSEnabledUseCaseImpl(featureSupport(), userConfigRepository())

    @Provides
    @UserSessionBinding("isE2EIEnabled")
    fun userSessionProvideIsE2EIEnabled(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("isMLSEnabled")
        isMLSEnabled: Provider<IsMLSEnabledUseCase>,
    ): IsE2EIEnabledUseCase =
        IsE2EIEnabledUseCaseImpl(
            userConfigRepository(),
            isMLSEnabled()
        )

    @Provides
    @UserSessionBinding("getDefaultProtocol")
    fun userSessionProvideGetDefaultProtocol(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): GetDefaultProtocolUseCase =
        GetDefaultProtocolUseCaseImpl(
            userConfigRepository = userConfigRepository()
        )

    @Provides
    @UserSessionBinding("observeE2EIRequired")
    fun userSessionProvideObserveE2EIRequired(
        @LegacyUserBinding("getE2EICertificate")
        userGetE2EICertificate: Provider<GetMLSClientIdentityUseCase>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): ObserveE2EIRequiredUseCase =
        ObserveE2EIRequiredUseCaseImpl(
            userConfigRepository(),
            featureSupport(),
            userGetE2EICertificate(),
            clientIdProvider()
        )

    @Provides
    @UserSessionBinding("markE2EIRequiredAsNotified")
    fun userSessionProvideMarkE2EIRequiredAsNotified(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkEnablingE2EIAsNotifiedUseCase =
        MarkEnablingE2EIAsNotifiedUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("isAllowedToRegisterMLSClient")
    fun userSessionProvideIsAllowedToRegisterMLSClient(
        @UserSessionBinding("featureConfigRepository")
        featureConfigRepository: Provider<FeatureConfigRepository>,
        @UserSessionBinding("mlsPublicKeysRepository")
        mlsPublicKeysRepository: Provider<MLSPublicKeysRepository>,
        @UserSessionBinding("featureSupport")
        featureSupport: Provider<FeatureSupport>,
    ): IsAllowedToRegisterMLSClientUseCase =
        IsAllowedToRegisterMLSClientUseCaseImpl(
            featureSupport(),
            mlsPublicKeysRepository(),
            featureConfigRepository()
        )

    @Provides
    @UserSessionBinding("syncFeatureConfigsUseCase")
    fun userSessionProvideSyncFeatureConfigsUseCase(
        @LegacyChannelsBinding("channelsFeatureConfigHandler")
        channelsChannelsFeatureConfigHandler: Provider<ChannelsFeatureConfigurationHandler>,
        @UserSessionBinding("secondFactorPasswordChallengeConfigHandler")
        secondFactorPasswordChallengeConfigHandler: Provider<SecondFactorPasswordChallengeConfigHandler>,
        @UserSessionBinding("consumableNotificationsConfigHandler")
        consumableNotificationsConfigHandler: Provider<ConsumableNotificationsConfigHandler>,
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
        @UserSessionBinding("featureConfigRepository")
        featureConfigRepository: Provider<FeatureConfigRepository>,
        @UserSessionBinding("guestRoomConfigHandler")
        guestRoomConfigHandler: Provider<GuestRoomConfigHandler>,
        @UserSessionBinding("meetingsConfigHandler")
        meetingsConfigHandler: Provider<MeetingsConfigHandler>,
        @UserSessionBinding("appLockConfigHandler")
        appLockConfigHandler: Provider<AppLockConfigHandler>,
        @UserSessionBinding("appsFeatureHandler")
        appsFeatureHandler: Provider<AppsFeatureHandler>,
        @UserSessionBinding("cellsConfigHandler")
        cellsConfigHandler: Provider<CellsConfigHandler>,
        @UserSessionBinding("e2eiConfigHandler")
        e2eiConfigHandler: Provider<E2EIConfigHandler>,
        @UserSessionBinding("mlsConfigHandler")
        mlsConfigHandler: Provider<MLSConfigHandler>,
    ): SyncFeatureConfigsUseCase =
        SyncFeatureConfigsUseCaseImpl(
            featureConfigRepository(),
            guestRoomConfigHandler(),
            fileSharingConfigHandler(),
            mlsConfigHandler(),
            mlsMigrationConfigHandler(),
            classifiedDomainsConfigHandler(),
            conferenceCallingConfigHandler(),
            secondFactorPasswordChallengeConfigHandler(),
            selfDeletingMessagesConfigHandler(),
            e2eiConfigHandler(),
            appLockConfigHandler(),
            channelsChannelsFeatureConfigHandler(),
            consumableNotificationsConfigHandler(),
            allowedGlobalOperationsHandler(),
            cellsConfigHandler(),
            appsFeatureHandler(),
            enableUserProfileQRCodeConfigHandler(),
            assetAuditLogConfigHandler(),
            preventAdminlessGroupsConfigHandler(),
            meetingsConfigHandler(),
        )

    @Provides
    @UserSessionBinding("observeSecurityClassificationLabel")
    fun userSessionProvideObserveSecurityClassificationLabel(
        @LegacyConversationBinding("observeConversationMembers")
        conversationObserveConversationMembers: Provider<ObserveConversationMembersUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveSecurityClassificationLabelUseCase =
        ObserveSecurityClassificationLabelUseCaseImpl(
            conversationObserveConversationMembers(),
            conversationRepository(),
            userConfigRepository()
        )

    @Provides
    @UserSessionBinding("getOtherUserSecurityClassificationLabel")
    fun userSessionProvideGetOtherUserSecurityClassificationLabel(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ObserveOtherUserSecurityClassificationLabelUseCase =
        ObserveOtherUserSecurityClassificationLabelUseCaseImpl(userConfigRepository(), userId())

    @Provides
    @UserSessionBinding("persistScreenshotCensoringConfig")
    fun userSessionProvidePersistScreenshotCensoringConfig(
        @UserSessionBinding("userPropertyRepository")
        userPropertyRepository: Provider<UserPropertyRepository>,
    ): PersistScreenshotCensoringConfigUseCase =
        PersistScreenshotCensoringConfigUseCaseImpl(userPropertyRepository = userPropertyRepository())

}
