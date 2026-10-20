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
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.app.AppDataSource
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteDataSource
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.call.CallDataSource
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.ClientDataSource
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.LegalHoldClientsImpl
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.connection.ConnectionDataSource
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCase
import com.wire.kalium.logic.data.conversation.FetchConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.CompositeMessageDataSource
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.message.PollMessageDataSource
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.message.draft.MessageDraftDataSource
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.notification.PushTokenDataSource
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapper
import com.wire.kalium.logic.data.publicuser.UserSearchApiWrapperImpl
import com.wire.kalium.logic.data.service.ServiceDataSource
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.session.SessionRepository
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
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCase
import com.wire.kalium.logic.feature.message.AddSystemMessageToAllConversationsUseCaseImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldSystemMessagesHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldTransitionJournal
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldUserTransitionJournal
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCaseImpl
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings2 {
    @Provides
    @UserSessionBinding("compositeMessageRepository")
    fun userSessionProvideCompositeMessageRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CompositeMessageRepository =
        CompositeMessageDataSource(compositeMessageDAO = userStorage().database.compositeMessageDAO)

    @Provides
    @UserSessionBinding("pollMessageRepository")
    fun userSessionProvidePollMessageRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PollMessageRepository =
        PollMessageDataSource(pollMessageDAO = userStorage().database.pollMessageDAO)

    @Provides
    @UserSessionBinding("messageDraftRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMessageDraftRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageDraftRepository =
        MessageDraftDataSource(messageDraftDAO = userStorage().database.messageDraftDAO)

    @Provides
    @UserSessionBinding("slowSyncRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSlowSyncRepository(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): SlowSyncRepository =
        SlowSyncRepositoryImpl(userStorage().database.metadataDAO, userScopedLogger())

    @Provides
    @UserSessionBinding("incrementalSyncRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideIncrementalSyncRepository(
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): IncrementalSyncRepository =
        InMemoryIncrementalSyncRepository(userScopedLogger())

    @Provides
    @UserSessionBinding("legalHoldSystemMessagesHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLegalHoldSystemMessagesHandler(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LegalHoldSystemMessagesHandlerImpl =
        LegalHoldSystemMessagesHandlerImpl(
            selfUserId = userId(),
            persistMessage = persistMessage(),
            conversationRepository = conversationRepository(),
            messageRepository = messageRepository()
        )

    @Provides
    @UserSessionBinding("legalHoldHandler")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideLegalHoldHandler(
        @UserSessionBinding("legalHoldHandlerLegalHoldUserTransitionJournal")
        legalHoldHandlerLegalHoldUserTransitionJournal: Provider<LegalHoldUserTransitionJournal>,
        @UserSessionBinding("legalHoldHandlerLegalHoldTransitionJournal")
        legalHoldHandlerLegalHoldTransitionJournal: Provider<LegalHoldTransitionJournal>,
        @UserSessionBinding("legalHoldHandlerLegalHoldClientsImpl")
        legalHoldHandlerLegalHoldClientsImpl: Provider<LegalHoldClientsImpl>,
        @UserSessionBinding("legalHoldSystemMessagesHandler")
        legalHoldSystemMessagesHandler: Provider<LegalHoldSystemMessagesHandlerImpl>,
        @UserSessionBinding("membersHavingLegalHoldClient")
        membersHavingLegalHoldClient: Provider<MembersHavingLegalHoldClientUseCase>,
        @UserSessionBinding("observeLegalHoldStateForUser")
        observeLegalHoldStateForUser: Provider<ObserveLegalHoldStateForUserUseCase>,
        @UserSessionBinding("fetchUsersClientsFromRemote")
        fetchUsersClientsFromRemote: Provider<FetchUsersClientsFromRemoteUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("observeSyncState")
        observeSyncState: Provider<ObserveSyncStateUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LegalHoldHandlerImpl =
        LegalHoldHandlerImpl(
            selfUserId = userId(),
            fetchUsersClientsFromRemote = fetchUsersClientsFromRemote(),
            observeLegalHoldStateForUser = observeLegalHoldStateForUser(),
            membersHavingLegalHoldClient = membersHavingLegalHoldClient(),
            userConfigRepository = userConfigRepository(),
            conversationRepository = conversationRepository(),
            transitions = legalHoldHandlerLegalHoldTransitionJournal(),
            userTransitions = legalHoldHandlerLegalHoldUserTransitionJournal(),
            legalHoldClients = legalHoldHandlerLegalHoldClientsImpl(),
            observeSyncState = observeSyncState(),
            legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler(),
        )

    @Provides
    @UserSessionBinding("userRepository")
    fun userSessionProvideUserRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("globalSessionRepository")
        globalSessionRepository: Provider<SessionRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserRepository =
        UserDataSource(
            userDAO = userStorage().database.userDAO,
            clientDAO = userStorage().database.clientDAO,
            memberDAO = userStorage().database.memberDAO,
            appDAO = userStorage().database.appDAO,
            selfApi = authenticatedNetworkContainer().selfApi,
            userDetailsApi = authenticatedNetworkContainer().userDetailsApi,
            upgradePersonalToTeamApi = authenticatedNetworkContainer().upgradePersonalToTeamApi,
            teamsApi = authenticatedNetworkContainer().teamsApi,
            sessionRepository = globalSessionRepository(),
            selfUserId = userId(),
            selfTeamIdProvider = selfTeamId(),
            legalHoldHandler = legalHoldHandler()
        )

    @Provides
    @UserSessionBinding("accountRepository")
    fun userSessionProvideAccountRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AccountRepository =
        AccountRepositoryImpl(
            userDAO = userStorage().database.userDAO,
            selfUserId = userId(),
            selfApi = authenticatedNetworkContainer().selfApi
        )

    @Provides
    @UserSessionBinding("pushTokenRepository")
    fun userSessionProvidePushTokenRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PushTokenRepository =
        PushTokenDataSource(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("teamRepository")
    fun userSessionProvideTeamRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("legalHoldRequestHandler")
        legalHoldRequestHandler: Provider<LegalHoldRequestHandlerImpl>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): TeamRepository =
        TeamDataSource(
            userStorage().database.userDAO,
            userStorage().database.userConfigDAO,
            userStorage().database.teamDAO,
            authenticatedNetworkContainer().teamsApi,
            userId(),
            userStorage().database.serviceDAO,
            userStorage().database.appDAO,
            legalHoldHandler(),
            legalHoldRequestHandler(),
        )

    @Provides
    @UserSessionBinding("serviceRepository")
    fun userSessionProvideServiceRepository(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ServiceRepository =
        ServiceDataSource(
            serviceDAO = userStorage().database.serviceDAO,
        )

    @Provides
    @UserSessionBinding("appRepository")
    fun userSessionProvideAppRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AppRepository =
        AppDataSource(
            selfUserId = userId(),
            appDAO = userStorage().database.appDAO,
            teamDAO = userStorage().database.teamDAO,
            teamsApi = authenticatedNetworkContainer().teamsApi,
            userDetailsApi = authenticatedNetworkContainer().userDetailsApi,
        )

    @Provides
    @UserSessionBinding("persistConversationsUseCase")
    fun userSessionProvidePersistConversationsUseCase(
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistConversationsUseCase =
        PersistConversationsUseCaseImpl(
            selfUserId = userId(),
            conversationRepository = conversationRepository(),
            selfTeamIdProvider = selfTeamId()
        )

    @Provides
    @UserSessionBinding("persistConversationUseCase")
    fun userSessionProvidePersistConversationUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): PersistConversationUseCase =
        PersistConversationUseCaseImpl(
            conversationRepository = conversationRepository(),
            persistConversations = persistConversationsUseCase(),
        )

    @Provides
    @UserSessionBinding("fetchMLSOneToOneConversationUseCase")
    fun userSessionProvideFetchMLSOneToOneConversationUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): FetchMLSOneToOneConversationUseCase =
        FetchMLSOneToOneConversationUseCaseImpl(
            selfUserId = userId(),
            conversationRepository = conversationRepository(),
            persistConversations = persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("fetchConversationUseCase")
    fun userSessionProvideFetchConversationUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): FetchConversationUseCase =
        FetchConversationUseCaseImpl(
            conversationRepository = conversationRepository(),
            persistConversations = persistConversationsUseCase(),
            transactionProvider = cryptoTransactionProvider(),
        )

    @Provides
    @UserSessionBinding("fetchConversationIfUnknownUseCase")
    fun userSessionProvideFetchConversationIfUnknownUseCase(
        @UserSessionBinding("fetchConversationUseCase")
        fetchConversationUseCase: Provider<FetchConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): FetchConversationIfUnknownUseCase =
        FetchConversationIfUnknownUseCaseImpl(
            conversationRepository = conversationRepository(),
            fetchConversation = fetchConversationUseCase()
        )

    @Provides
    @UserSessionBinding("fetchConversationsUseCase")
    fun userSessionProvideFetchConversationsUseCase(
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
    ): FetchConversationsUseCase =
        FetchConversationsUseCaseImpl(
            conversationRepository = conversationRepository(),
            persistConversations = persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("connectionRepository")
    fun userSessionProvideConnectionRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("persistConversationsUseCase")
        persistConversationsUseCase: Provider<PersistConversationsUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConnectionRepository =
        ConnectionDataSource(
            userStorage().database.conversationDAO,
            userStorage().database.memberDAO,
            userStorage().database.connectionDAO,
            authenticatedNetworkContainer().connectionApi,
            userStorage().database.userDAO,
            conversationRepository(),
            persistConversationsUseCase()
        )

    @Provides
    @UserSessionBinding("userSearchApiWrapper")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideUserSearchApiWrapper(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserSearchApiWrapper =
        UserSearchApiWrapperImpl(
            authenticatedNetworkContainer().userSearchApi,
            userStorage().database.memberDAO,
            userId()
        )

    @Provides
    @UserSessionBinding("searchUserRepository")
    fun userSessionProvideSearchUserRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("userSearchApiWrapper")
        userSearchApiWrapper: Provider<UserSearchApiWrapper>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SearchUserRepository =
        SearchUserRepositoryImpl(
            userStorage().database.userDAO,
            userStorage().database.searchDAO,
            userStorage().database.appDAO,
            authenticatedNetworkContainer().userDetailsApi,
            userSearchApiWrapper(),
            userId(),
            selfTeamId()
        )

    @Provides
    @UserSessionBinding("cryptoStateBackupRemoteRepository")
    fun userSessionProvideCryptoStateBackupRemoteRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
    ): CryptoStateBackupRemoteRepository =
        CryptoStateBackupRemoteDataSource(authenticatedNetworkContainer().nomadDeviceSyncApi)

    @Provides
    @UserSessionBinding("persistMessage")
    fun userSessionProvidePersistMessage(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): PersistMessageUseCase =
        PersistMessageUseCaseImpl(
            messageRepository = messageRepository(),
            selfUserId = userId(),
            notificationEventsManager = notificationEventsManager(),
            persistMessageHookNotifier = currentPersistenceEventHookNotifier()
        )

    @Provides
    @UserSessionBinding("addSystemMessageToAllConversationsUseCase")
    fun userSessionProvideAddSystemMessageToAllConversationsUseCase(
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): AddSystemMessageToAllConversationsUseCase =
        AddSystemMessageToAllConversationsUseCaseImpl(messageRepository(), userId())

    @Provides
    @UserSessionBinding("restartSlowSyncProcessForRecoveryUseCase")
    fun userSessionProvideRestartSlowSyncProcessForRecoveryUseCase(
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
    ): RestartSlowSyncProcessForRecoveryUseCase =
        RestartSlowSyncProcessForRecoveryUseCaseImpl(slowSyncRepository())

    @Provides
    @UserSessionBinding("callRepository")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideCallRepository(
        @UserSessionBinding("pendingSubconversationProposals")
        pendingSubconversationProposals: Provider<PendingSubconversationProposals>,
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("leaveSubconversationUseCase")
        leaveSubconversationUseCase: Provider<LeaveSubconversationUseCase>,
        @UserSessionBinding("joinSubconversationUseCase")
        joinSubconversationUseCase: Provider<JoinSubconversationUseCase>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("subconversationRepository")
        subconversationRepository: Provider<SubconversationRepositoryImpl>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("epochChangesObserver")
        epochChangesObserver: Provider<EpochChangesObserver>,
        @UserSessionBinding("federatedIdMapper")
        federatedIdMapper: Provider<FederatedIdMapper>,
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("persistMessage")
        persistMessage: Provider<PersistMessageUseCase>,
        @UserSessionBinding("teamRepository")
        teamRepository: Provider<TeamRepository>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("callMapper")
        callMapper: Provider<CallMapper>,
    ): CallRepository =
        CallDataSource(
            callApi = authenticatedNetworkContainer().callApi,
            sftApi = authenticatedNetworkContainer().sftApi,
            serverTimeApi = authenticatedNetworkContainer().serverTimeApi,
            qualifiedIdMapper = qualifiedIdMapper(),
            callDAO = userStorage().database.callDAO,
            conversationRepository = conversationRepository(),
            mlsConversationRepository = mlsConversationRepository(),
            subconversationRepository = subconversationRepository(),
            joinSubconversation = joinSubconversationUseCase(),
            leaveSubconversation = leaveSubconversationUseCase(),
            subgroupProposals = pendingSubconversationProposals(),
            userRepository = userRepository(),
            epochChangesObserver = epochChangesObserver(),
            teamRepository = teamRepository(),
            persistMessage = persistMessage(),
            callMapper = callMapper(),
            federatedIdMapper = federatedIdMapper(),
            transactionProvider = cryptoTransactionProvider()
        )

    @Provides
    @UserSessionBinding("clientRemoteRepository")
    fun userSessionProvideClientRemoteRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientConfig")
        clientConfig: Provider<ClientConfig>,
    ): ClientRemoteRepository =
        ClientRemoteDataSource(
            authenticatedNetworkContainer().clientApi,
            clientConfig()
        )

    @Provides
    @UserSessionBinding("clientRegistrationStorage")
    fun userSessionProvideClientRegistrationStorage(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ClientRegistrationStorage =
        ClientRegistrationStorageImpl(userStorage().database.metadataDAO)

    @Provides
    @UserSessionBinding("clientRepository")
    fun userSessionProvideClientRepository(
        @UserSessionBinding("authenticatedNetworkContainer")
        authenticatedNetworkContainer: Provider<AuthenticatedNetworkContainer>,
        @UserSessionBinding("clientRegistrationStorage")
        clientRegistrationStorage: Provider<ClientRegistrationStorage>,
        @UserSessionBinding("clientRemoteRepository")
        clientRemoteRepository: Provider<ClientRemoteRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ClientRepository =
        ClientDataSource(
            clientRemoteRepository(),
            clientRegistrationStorage(),
            userStorage().database.clientDAO,
            userStorage().database.newClientDAO,
            userId(),
            authenticatedNetworkContainer().clientApi,
        )

    @Provides
    @UserSessionBinding("messageSendingScheduler")
    fun userSessionProvideMessageSendingScheduler(
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
    ): MessageSendingScheduler =
        userSessionWorkScheduler()

    @Provides
    @UserSessionBinding("audioNormalizedLoudnessScheduler")
    fun userSessionProvideAudioNormalizedLoudnessScheduler(
        @UserSessionBinding("userSessionWorkScheduler")
        userSessionWorkScheduler: Provider<UserSessionWorkScheduler>,
    ): AudioNormalizedLoudnessScheduler =
        userSessionWorkScheduler()

}
