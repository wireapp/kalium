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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SystemMessageInserterImpl
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.analytics.AnalyticsIdentifierManager
import com.wire.kalium.logic.feature.analytics.GetCurrentAnalyticsTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.analytics.ObserveAnalyticsTrackingIdentifierStatusUseCase
import com.wire.kalium.logic.feature.analytics.SetNewUserTrackingIdentifierUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCaseImpl
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConsumableNotificationsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
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
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiver
import com.wire.kalium.logic.sync.receiver.ConversationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.FederationEventReceiver
import com.wire.kalium.logic.sync.receiver.FederationEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.TeamEventReceiver
import com.wire.kalium.logic.sync.receiver.TeamEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserEventReceiver
import com.wire.kalium.logic.sync.receiver.UserEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiver
import com.wire.kalium.logic.sync.receiver.UserPropertiesEventReceiverImpl
import com.wire.kalium.logic.sync.receiver.conversation.AccessUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ChannelAddPermissionUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeleteConversationReminderEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.DeletedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MLSResetConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.MLSWelcomeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberChangeEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.NewConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.ProtocolUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandler
import com.wire.kalium.logic.sync.receiver.handler.TypingIndicatorHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.UserPropertiesEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings6 {
    @Provides
    @UserSessionBinding("conversationCodeDeletedHandler")
    fun userSessionProvideConversationCodeDeletedHandler(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CodeDeletedHandler =
        CodeDeletedHandlerImpl(
            conversationDAO = userStorage().database.conversationDAO
        )

    @Provides
    @UserSessionBinding("typingIndicatorHandler")
    fun userSessionProvideTypingIndicatorHandler(
        @LegacyConversationBinding("typingIndicatorIncomingRepository")
        conversationTypingIndicatorIncomingRepository: Provider<TypingIndicatorIncomingRepositoryImpl>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): TypingIndicatorHandler =
        TypingIndicatorHandlerImpl(userId(), conversationTypingIndicatorIncomingRepository())

    @Provides
    @UserSessionBinding("protocolUpdateEventHandler")
    fun userSessionProvideProtocolUpdateEventHandler(
        @UserSessionBinding("updateConversationProtocolUseCase")
        updateConversationProtocolUseCase: Provider<UpdateConversationProtocolUseCase>,
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
    ): ProtocolUpdateEventHandler =
        ProtocolUpdateEventHandlerImpl(
            systemMessageInserter = systemMessageInserter(),
            callRepository = callRepository(),
            updateConversationProtocolUseCase()
        )

    @Provides
    @UserSessionBinding("channelAddPermissionUpdateEventHandler")
    fun userSessionProvideChannelAddPermissionUpdateEventHandler(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): ChannelAddPermissionUpdateEventHandler =
        ChannelAddPermissionUpdateEventHandlerImpl(
            conversationRepository = conversationRepository()
        )

    @Provides
    @UserSessionBinding("conversationAccessUpdateEventHandler")
    fun userSessionProvideConversationAccessUpdateEventHandler(
        @UserSessionBinding("systemMessageInserter")
        systemMessageInserter: Provider<SystemMessageInserterImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccessUpdateEventHandler =
        AccessUpdateEventHandler(
            conversationDAO = userStorage().database.conversationDAO,
            selfUserId = userId(),
            systemMessageInserter = systemMessageInserter()
        )

    @Provides
    @UserSessionBinding("mlsResetConversationEventHandler")
    fun userSessionProvideMlsResetConversationEventHandler(
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("endCallOnMLSReset")
        endCallOnMLSReset: Provider<EndCallOnMLSResetUseCase>,
    ): MLSResetConversationEventHandler =
        MLSResetConversationEventHandlerImpl(
            mlsConversationRepository = mlsConversationRepository(),
            endCallOnMLSReset = endCallOnMLSReset(),
        )

    @Provides
    @UserSessionBinding("conversationEventReceiver")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideConversationEventReceiver(
        @UserSessionBinding("channelAddPermissionUpdateEventHandler")
        channelAddPermissionUpdateEventHandler: Provider<ChannelAddPermissionUpdateEventHandler>,
        @UserSessionBinding("deleteConversationReminderEventHandler")
        deleteConversationReminderEventHandler: Provider<DeleteConversationReminderEventHandler>,
        @UserSessionBinding("conversationAccessUpdateEventHandler")
        conversationAccessUpdateEventHandler: Provider<AccessUpdateEventHandler>,
        @UserSessionBinding("conversationMessageTimerEventHandler")
        conversationMessageTimerEventHandler: Provider<ConversationMessageTimerEventHandler>,
        @UserSessionBinding("mlsResetConversationEventHandler")
        mlsResetConversationEventHandler: Provider<MLSResetConversationEventHandler>,
        @UserSessionBinding("conversationCodeDeletedHandler")
        conversationCodeDeletedHandler: Provider<CodeDeletedHandler>,
        @UserSessionBinding("conversationCodeUpdateHandler")
        conversationCodeUpdateHandler: Provider<CodeUpdatedHandler>,
        @UserSessionBinding("receiptModeUpdateEventHandler")
        receiptModeUpdateEventHandler: Provider<ReceiptModeUpdateEventHandler>,
        @UserSessionBinding("deletedConversationHandler")
        deletedConversationHandler: Provider<DeletedConversationEventHandler>,
        @UserSessionBinding("protocolUpdateEventHandler")
        protocolUpdateEventHandler: Provider<ProtocolUpdateEventHandler>,
        @UserSessionBinding("renamedConversationHandler")
        renamedConversationHandler: Provider<RenamedConversationEventHandler>,
        @UserSessionBinding("newConversationHandler")
        newConversationHandler: Provider<NewConversationEventHandler>,
        @UserSessionBinding("typingIndicatorHandler")
        typingIndicatorHandler: Provider<TypingIndicatorHandler>,
        @UserSessionBinding("memberChangeHandler")
        memberChangeHandler: Provider<MemberChangeEventHandler>,
        @UserSessionBinding("memberLeaveHandler")
        memberLeaveHandler: Provider<MemberLeaveEventHandler>,
        @UserSessionBinding("memberJoinHandler")
        memberJoinHandler: Provider<MemberJoinEventHandler>,
        @UserSessionBinding("mlsWelcomeHandler")
        mlsWelcomeHandler: Provider<MLSWelcomeEventHandler>,
        @UserSessionBinding("newMessageHandler")
        newMessageHandler: Provider<NewMessageEventHandler>,
    ): ConversationEventReceiver =
        ConversationEventReceiverImpl(
            newMessageHandler(),
            newConversationHandler(),
            deletedConversationHandler(),
            deleteConversationReminderEventHandler(),
            memberJoinHandler(),
            memberLeaveHandler(),
            memberChangeHandler(),
            mlsWelcomeHandler(),
            renamedConversationHandler(),
            receiptModeUpdateEventHandler(),
            conversationMessageTimerEventHandler(),
            conversationCodeUpdateHandler(),
            conversationCodeDeletedHandler(),
            typingIndicatorHandler(),
            protocolUpdateEventHandler(),
            channelAddPermissionUpdateEventHandler(),
            conversationAccessUpdateEventHandler(),
            mlsResetConversationEventHandler(),
        )

    @Provides
    @UserSessionBinding("legalHoldRequestHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLegalHoldRequestHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LegalHoldRequestHandlerImpl =
        LegalHoldRequestHandlerImpl(
            selfUserId = userId(),
            userConfigRepository = userConfigRepository()
        )

    @Provides
    @UserSessionBinding("observeLegalHoldStateForUser")
    fun userSessionProvideObserveLegalHoldStateForUser(
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): ObserveLegalHoldStateForUserUseCase =
        ObserveLegalHoldStateForUserUseCaseImpl(clientRepository())

    @Provides
    @UserSessionBinding("observeAnalyticsTrackingIdentifierStatus")
    fun userSessionProvideObserveAnalyticsTrackingIdentifierStatus(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): ObserveAnalyticsTrackingIdentifierStatusUseCase =
        ObserveAnalyticsTrackingIdentifierStatusUseCase(userConfigRepository(), userScopedLogger())

    @Provides
    @UserSessionBinding("setNewUserTrackingIdentifier")
    fun userSessionProvideSetNewUserTrackingIdentifier(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): SetNewUserTrackingIdentifierUseCase =
        SetNewUserTrackingIdentifierUseCase(userConfigRepository())

    @Provides
    @UserSessionBinding("getCurrentAnalyticsTrackingIdentifier")
    fun userSessionProvideGetCurrentAnalyticsTrackingIdentifier(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): GetCurrentAnalyticsTrackingIdentifierUseCase =
        GetCurrentAnalyticsTrackingIdentifierUseCase(userConfigRepository())

    @Provides
    @UserSessionBinding("analyticsIdentifierManager")
    fun userSessionProvideAnalyticsIdentifierManager(
        @LegacyMessageBinding("messageSender")
        messageMessageSender: Provider<MessageSender>,
        @UserSessionBinding("selfConversationIdProvider")
        selfConversationIdProvider: Provider<SelfConversationIdProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("syncManager")
        syncManager: Provider<SyncManager>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AnalyticsIdentifierManager =
        AnalyticsIdentifierManager(
            messageMessageSender(),
            userConfigRepository(),
            userId(),
            clientIdProvider(),
            selfConversationIdProvider(),
            syncManager(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("observeLegalHoldForSelfUser")
    fun userSessionProvideObserveLegalHoldForSelfUser(
        @UserSessionBinding("observeLegalHoldStateForUser")
        observeLegalHoldStateForUser: Provider<ObserveLegalHoldStateForUserUseCase>,
        @UserSessionBinding("observeLegalHoldRequest")
        observeLegalHoldRequest: Provider<ObserveLegalHoldRequestUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ObserveLegalHoldStateForSelfUserUseCase =
        ObserveLegalHoldStateForSelfUserUseCaseImpl(userId(), observeLegalHoldStateForUser(), observeLegalHoldRequest())

    @Provides
    @UserSessionBinding("observeLegalHoldChangeNotifiedForSelf")
    fun userSessionProvideObserveLegalHoldChangeNotifiedForSelf(
        @UserSessionBinding("observeLegalHoldStateForUser")
        observeLegalHoldStateForUser: Provider<ObserveLegalHoldStateForUserUseCase>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ObserveLegalHoldChangeNotifiedForSelfUseCase =
        ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl(userId(), userConfigRepository(), observeLegalHoldStateForUser())

    @Provides
    @UserSessionBinding("markLegalHoldChangeAsNotifiedForSelf")
    fun userSessionProvideMarkLegalHoldChangeAsNotifiedForSelf(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MarkLegalHoldChangeAsNotifiedForSelfUseCase =
        MarkLegalHoldChangeAsNotifiedForSelfUseCaseImpl(userConfigRepository())

    @Provides
    @UserSessionBinding("observeLegalHoldRequest")
    fun userSessionProvideObserveLegalHoldRequest(
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ObserveLegalHoldRequestUseCase =
        ObserveLegalHoldRequestUseCaseImpl(
            userConfigRepository = userConfigRepository(),
            transactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("approveLegalHoldRequest")
    fun userSessionProvideApproveLegalHoldRequest(
        @UserSessionBinding("teamRepository")
        teamRepository: Provider<TeamRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
    ): ApproveLegalHoldRequestUseCase =
        ApproveLegalHoldRequestUseCaseImpl(
            teamRepository = teamRepository(),
            selfTeamIdProvider = selfTeamId(),
        )

    @Provides
    @UserSessionBinding("fetchUsersClientsFromRemote")
    fun userSessionProvideFetchUsersClientsFromRemote(
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): FetchUsersClientsFromRemoteUseCase =
        FetchUsersClientsFromRemoteUseCaseImpl(
            clientRemoteRepository = clientRemoteRepository(),
            clientRepository = clientRepository()
        )

    @Provides
    @UserSessionBinding("membersHavingLegalHoldClient")
    fun userSessionProvideMembersHavingLegalHoldClient(
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
    ): MembersHavingLegalHoldClientUseCase =
        MembersHavingLegalHoldClientUseCaseImpl(clientRepository())

    @Provides
    @UserSessionBinding("fetchLegalHoldForSelfUserFromRemoteUseCase")
    fun userSessionProvideFetchLegalHoldForSelfUserFromRemoteUseCase(
        @UserSessionBinding("teamRepository")
        teamRepository: Provider<TeamRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
    ): FetchLegalHoldForSelfUserFromRemoteUseCase =
        FetchLegalHoldForSelfUserFromRemoteUseCaseImpl(
            teamRepository = teamRepository(),
            selfTeamIdProvider = selfTeamId(),
        )

    @Provides
    @UserSessionBinding("userEventReceiver")
    fun userSessionProvideUserEventReceiver(
        @UserSessionBinding("newGroupConversationSystemMessagesCreator")
        newGroupConversationSystemMessagesCreator: Provider<NewGroupConversationSystemMessagesCreator>,
        @UserSessionBinding("sessionRefreshSuggestedEventHandler")
        sessionRefreshSuggestedEventHandler: Provider<SessionRefreshSuggestedEventHandler>,
        @UserSessionBinding("legalHoldRequestHandler")
        legalHoldRequestHandler: Provider<LegalHoldRequestHandlerImpl>,
        @UserSessionBinding("connectionRepository")
        connectionRepository: Provider<ConnectionRepository>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("oneOnOneResolver")
        oneOnOneResolver: Provider<OneOnOneResolver>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserEventReceiver =
        UserEventReceiverImpl(
            clientRepository(),
            connectionRepository(),
            userRepository(),
            logout(),
            oneOnOneResolver(),
            userId(),
            clientIdProvider(),
            lazy { newGroupConversationSystemMessagesCreator() },
            legalHoldRequestHandler(),
            legalHoldHandler(),
            sessionRefreshSuggestedEventHandler()
        )

    @Provides
    @UserSessionBinding("userPropertiesEventReceiver")
    fun userSessionProvideUserPropertiesEventReceiver(
        @UserSessionBinding("userPropertiesEventReceiverUserPropertiesEventHandler")
        userPropertiesEventReceiverUserPropertiesEventHandler: Provider<UserPropertiesEventHandler>,
    ): UserPropertiesEventReceiver =
        UserPropertiesEventReceiverImpl(
            userPropertiesEventReceiverUserPropertiesEventHandler()
        )

    @Provides
    @UserSessionBinding("federationEventReceiver")
    fun userSessionProvideFederationEventReceiver(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("connectionRepository")
        connectionRepository: Provider<ConnectionRepository>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FederationEventReceiver =
        FederationEventReceiverImpl(
            conversationRepository(),
            connectionRepository(),
            userRepository(),
            userStorage().database.memberDAO,
            persistMessage(),
            userId()
        )

    @Provides
    @UserSessionBinding("teamEventReceiver")
    fun userSessionProvideTeamEventReceiver(
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): TeamEventReceiver =
        TeamEventReceiverImpl(userRepository(), persistMessage(), userId())

    @Provides
    @UserSessionBinding("guestRoomConfigHandler")
    fun userSessionProvideGuestRoomConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
    ): GuestRoomConfigHandler =
        GuestRoomConfigHandler(userConfigRepository(), kaliumConfigs().guestRoomLink)

    @Provides
    @UserSessionBinding("fileSharingConfigHandler")
    fun userSessionProvideFileSharingConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): FileSharingConfigHandler =
        FileSharingConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("mlsConfigHandler")
    fun userSessionProvideMlsConfigHandler(
        @UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
        updateSupportedProtocolsAndResolveOneOnOnes: Provider<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MLSConfigHandler =
        MLSConfigHandler(userConfigRepository(), updateSupportedProtocolsAndResolveOneOnOnes(), cryptoTransactionProvider())

    @Provides
    @UserSessionBinding("mlsMigrationConfigHandler")
    fun userSessionProvideMlsMigrationConfigHandler(
        @UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
        updateSupportedProtocolsAndResolveOneOnOnes: Provider<UpdateSupportedProtocolsAndResolveOneOnOnesUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): MLSMigrationConfigHandler =
        MLSMigrationConfigHandler(userConfigRepository(), updateSupportedProtocolsAndResolveOneOnOnes(), cryptoTransactionProvider())

    @Provides
    @UserSessionBinding("classifiedDomainsConfigHandler")
    fun userSessionProvideClassifiedDomainsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ClassifiedDomainsConfigHandler =
        ClassifiedDomainsConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("conferenceCallingConfigHandler")
    fun userSessionProvideConferenceCallingConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ConferenceCallingConfigHandler =
        ConferenceCallingConfigHandler(userConfigRepository())

    @Provides
    @UserSessionBinding("consumableNotificationsConfigHandler")
    fun userSessionProvideConsumableNotificationsConfigHandler(
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
    ): ConsumableNotificationsConfigHandler =
        ConsumableNotificationsConfigHandler(userConfigRepository())

}
