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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.mls.ConversationProtocolGetterImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandlerImpl
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseImpl
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.feature.conversation.LegacyConversationBinding
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.feature.debug.OptimizeDatabaseUseCaseImpl
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.ApiMigrationV3
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import com.wire.kalium.logic.sync.incremental.EventBatchProcessor
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.receiver.conversation.message.AppMLSReceiveRecovery
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.PollVoteHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.UserPropertiesEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandlerImpl
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.receiving.PendingConversationCleanup
import com.wire.kalium.logic.sync.slow.SyncNomadMessagesDuringSlowSyncUseCaseImpl
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderImpl
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Individual qualified construction edges.
internal interface UserSessionBindings10 {
    @Provides
    @UserSessionBinding("slowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl")
    fun userSessionProvideSlowSyncWorkerSyncNomadMessagesDuringSlowSyncUseCaseImpl(
        @UserSessionBinding("userAuthenticatedNetworkProvider")
        userAuthenticatedNetworkProvider: Provider<UserAuthenticatedNetworkProvider>,
        @UserSessionBinding("userStorageProvider")
        userStorageProvider: Provider<UserStorageProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("nomadServiceUrl")
        nomadServiceUrl: Provider<String?>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SyncNomadMessagesDuringSlowSyncUseCaseImpl =
        SyncNomadMessagesDuringSlowSyncUseCaseImpl(
            selfUserId = userId(),
            nomadServiceUrl = nomadServiceUrl(),
            userStorageProvider = userStorageProvider(),
            userAuthenticatedNetworkProvider = userAuthenticatedNetworkProvider(),
            logger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("slowSyncWorkerOptimizeDatabaseUseCaseImpl")
    fun userSessionProvideSlowSyncWorkerOptimizeDatabaseUseCaseImpl(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): OptimizeDatabaseUseCaseImpl =
        OptimizeDatabaseUseCaseImpl(userStorage().database.databaseOptimizer)

    @Provides
    @UserSessionBinding("apiMigrationsApiMigrationV3")
    fun userSessionProvideApiMigrationsApiMigrationV3(
        @UserSessionBinding("upgradeCurrentSessionUseCase")
        upgradeCurrentSessionUseCase: Provider<UpgradeCurrentSessionUseCaseImpl>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
    ): ApiMigrationV3 =
        ApiMigrationV3(clientIdProvider(), upgradeCurrentSessionUseCase())

    @Provides
    @UserSessionBinding("applicationMessageHandlerMessageTextEditHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerMessageTextEditHandlerImpl(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): MessageTextEditHandlerImpl =
        MessageTextEditHandlerImpl(messageRepository(), userStorage().database.messageDAO, notificationEventsManager())

    @Provides
    @UserSessionBinding("applicationMessageHandlerMessageMultipartEditHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerMessageMultipartEditHandlerImpl(
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
    ): MessageMultipartEditHandlerImpl =
        MessageMultipartEditHandlerImpl(messageRepository(), notificationEventsManager())

    @Provides
    @UserSessionBinding("applicationMessageHandlerLastReadContentHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerLastReadContentHandlerImpl(
        @UserSessionBinding("isMessageSentInSelfConversation")
        isMessageSentInSelfConversation: Provider<IsMessageSentInSelfConversationUseCase>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): LastReadContentHandlerImpl =
        LastReadContentHandlerImpl(
            conversationRepository(),
            userId(),
            isMessageSentInSelfConversation(),
            notificationEventsManager()
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerClearConversationContentHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerClearConversationContentHandlerImpl(
        @LegacyConversationBinding("clearConversationAssetsLocally")
        conversationClearConversationAssetsLocally: Provider<ClearConversationAssetsLocallyUseCase>,
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("isMessageSentInSelfConversation")
        isMessageSentInSelfConversation: Provider<IsMessageSentInSelfConversationUseCase>,
        @UserSessionBinding("pendingConversationCleanup")
        pendingConversationCleanup: Provider<PendingConversationCleanup>,
        @UserSessionBinding("deleteConversationUseCase")
        deleteConversationUseCase: Provider<DeleteConversationUseCase>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): ClearConversationContentHandlerImpl =
        ClearConversationContentHandlerImpl(
            conversationRepository(),
            userId(),
            isMessageSentInSelfConversation(),
            conversationClearConversationAssetsLocally(),
            deleteConversationUseCase(),
            currentPersistenceEventHookNotifier(),
            recoverNSEClear = { message, content ->
                enableDurableMLSReceiveRecovery() &&
                    pendingConversationCleanup().recover(message.conversationId.toDao(), message.id, content.conversationId.toDao())
            },
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerDeleteForMeHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerDeleteForMeHandlerImpl(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("isMessageSentInSelfConversation")
        isMessageSentInSelfConversation: Provider<IsMessageSentInSelfConversationUseCase>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeleteForMeHandlerImpl =
        DeleteForMeHandlerImpl(
            userStorage().database.messageDAO,
            isMessageSentInSelfConversation(),
            currentPersistenceEventHookNotifier(),
            userId(),
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerDeleteMessageHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerDeleteMessageHandlerImpl(
        @UserSessionBinding("currentPersistenceEventHookNotifier")
        currentPersistenceEventHookNotifier: Provider<PersistenceEventHookNotifier>,
        @UserSessionBinding("notificationEventsManager")
        notificationEventsManager: Provider<NotificationEventsManager>,
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
        @UserSessionBinding("assetRepository")
        assetRepository: Provider<AssetRepository>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): DeleteMessageHandlerImpl =
        DeleteMessageHandlerImpl(
            messageRepository(),
            userStorage().database.messageDAO,
            assetRepository(),
            notificationEventsManager(),
            userId(),
            currentPersistenceEventHookNotifier()
        )

    @Provides
    @UserSessionBinding("applicationMessageHandlerMessageCompositeEditHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerMessageCompositeEditHandlerImpl(
        @UserSessionBinding("messageRepository")
        messageRepository: Provider<MessageRepository>,
    ): MessageCompositeEditHandlerImpl =
        MessageCompositeEditHandlerImpl(messageRepository())

    @Provides
    @UserSessionBinding("applicationMessageHandlerPollVoteHandlerImpl")
    fun userSessionProvideApplicationMessageHandlerPollVoteHandlerImpl(
        @UserSessionBinding("pollMessageRepository")
        pollMessageRepository: Provider<PollMessageRepository>,
    ): PollVoteHandlerImpl =
        PollVoteHandlerImpl(pollMessageRepository())

    @Provides
    @UserSessionBinding("userPropertiesEventReceiverUserPropertiesEventHandler")
    fun userSessionProvideUserPropertiesEventReceiverUserPropertiesEventHandler(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): UserPropertiesEventHandler =
        UserPropertiesEventHandler(
            userStorage().database.userPrefsDAO,
            userStorage().database.conversationFolderDAO
        )

    @Provides
    @UserSessionBinding("newMlsMissingUsersRejectionHandler")
    fun userSessionProvideNewMlsMissingUsersRejectionHandler(
        @UserSessionBinding("mlsRejectionConversationProtocolGetter")
        mlsRejectionConversationProtocolGetter: Provider<ConversationProtocolGetterImpl>,
        @UserSessionBinding("mlsConversationRepository")
        mlsConversationRepository: Provider<MLSConversationRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): MLSMissingUsersMessageRejectionHandlerImpl =
        MLSMissingUsersMessageRejectionHandlerImpl(
            mlsConversationRepository(),
            mlsRejectionConversationProtocolGetter(),
            userScopedLogger()
        )

    @Provides
    @UserSessionBinding("mlsRejectionConversationProtocolGetter")
    fun userSessionProvideMlsRejectionConversationProtocolGetter(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): ConversationProtocolGetterImpl =
        ConversationProtocolGetterImpl(userStorage().database.conversationDAO)

    @Provides
    @UserSessionBinding("mlsClientManagerRegisterMLSClientUseCaseImpl")
    fun userSessionProvideMlsClientManagerRegisterMLSClientUseCaseImpl(
        @UserSessionBinding("currentCryptoStateChangeHookNotifier")
        currentCryptoStateChangeHookNotifier: Provider<CryptoStateChangeHookNotifier>,
        @UserSessionBinding("keyPackageLimitsProvider")
        keyPackageLimitsProvider: Provider<KeyPackageLimitsProvider>,
        @UserSessionBinding("keyPackageRepository")
        keyPackageRepository: Provider<KeyPackageRepository>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("mlsClientProvider")
        mlsClientProvider: Provider<MLSClientProvider>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): RegisterMLSClientUseCaseImpl =
        RegisterMLSClientUseCaseImpl(
            mlsClientProvider(),
            clientRepository(),
            keyPackageRepository(),
            keyPackageLimitsProvider(),
            userConfigRepository(),
            userId(),
            currentCryptoStateChangeHookNotifier()
        )

    @Provides
    @UserSessionBinding("syncMigrationStepsProviderSyncMigrationStepsProviderImpl")
    fun userSessionProvideSyncMigrationStepsProviderSyncMigrationStepsProviderImpl(
        @UserSessionBinding("platformUserStorageProperties")
        platformUserStorageProperties: Provider<PlatformUserStorageProperties>,
        @UserSessionBinding("accountRepository")
        accountRepository: Provider<AccountRepository>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("selfTeamId")
        selfTeamId: Provider<SelfTeamIdProvider>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): SyncMigrationStepsProviderImpl =
        SyncMigrationStepsProviderImpl(
            accountRepository = lazy { accountRepository() },
            selfTeamIdProvider = selfTeamId(),
            oldUserConfigStorage = lazy {
                UserConfigStorageFactory().create(userId(), kaliumConfigs().shouldEncryptData(), platformUserStorageProperties())
            },
            newUserConfigStorage = lazy { userStorage().database.userPrefsDAO }
        )

    @Provides
    @UserSessionBinding("syncStateObserverSyncStateObserverImpl")
    fun userSessionProvideSyncStateObserverSyncStateObserverImpl(
        @UserSessionBinding("incrementalSyncRepository")
        incrementalSyncRepository: Provider<IncrementalSyncRepository>,
        @UserSessionBinding("sessionCoroutineScope")
        sessionCoroutineScope: Provider<CoroutineScope>,
        @UserSessionBinding("slowSyncRepository")
        slowSyncRepository: Provider<SlowSyncRepository>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
    ): SyncStateObserverImpl =
        SyncStateObserverImpl(
            slowSyncRepository = slowSyncRepository(),
            incrementalSyncRepository = incrementalSyncRepository(),
            syncScope = sessionCoroutineScope(),
            logger = userScopedLogger()
        )

    @Provides
    @UserSessionBinding("newUpdateConversationClientsForCurrentCall")
    fun userSessionProvideNewUpdateConversationClientsForCurrentCall(
        @UserSessionBinding("conversationClientsInCallUpdater")
        conversationClientsInCallUpdater: Provider<ConversationClientsInCallUpdater>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
    ): UpdateConversationClientsForCurrentCallUseCaseImpl =
        UpdateConversationClientsForCurrentCallUseCaseImpl(callRepository(), conversationClientsInCallUpdater())

    @Provides
    @UserSessionBinding("pendingConversationCleanup")
    fun userSessionProvidePendingConversationCleanup(
        @UserSessionBinding("kaliumFileSystem")
        kaliumFileSystem: Provider<KaliumFileSystem>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): PendingConversationCleanup =
        PendingConversationCleanup(userStorage().database) {
            kaliumFileSystem().delete(it.toPath(), mustExist = false)
        }

    @Provides
    @UserSessionBinding("eventBatchProcessor")
    fun userSessionProvideEventBatchProcessor(
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("eventRepository")
        eventRepository: Provider<EventRepository>,
        @UserSessionBinding("receiveRecovery")
        receiveRecovery: Provider<UserSessionValue<suspend () -> AppMLSReceiveRecovery?>>,
        @UserSessionBinding("eventProcessor")
        eventProcessor: Provider<EventProcessor>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): EventBatchProcessor =
        if (enableDurableMLSReceiveRecovery()) {
            EventBatchProcessor.durable(
                eventProcessor = eventProcessor(),
                transactionProvider = cryptoTransactionProvider(),
                databaseBuilder = userStorage().database,
                recovery = { checkNotNull(receiveRecovery().value()) },
                logger = userScopedLogger(),
            )
        } else {
            EventBatchProcessor.standard(
                eventProcessor = eventProcessor(),
                transactionProvider = cryptoTransactionProvider(),
                databaseBuilder = userStorage().database,
                eventRepository = eventRepository(),
                logger = userScopedLogger(),
            )
        }

    @Provides
    @UserSessionBinding("callSignalJournal")
    fun userSessionProvideCallSignalJournal(
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
    ): CallSignalJournal =
        CallSignalJournal(userStorage().database)

    @Provides
    @UserSessionBinding("incrementalWorkerFactory")
    fun userSessionProvideIncrementalWorkerFactory(
        @UserSessionBinding("enableDurableMLSReceiveRecovery")
        enableDurableMLSReceiveRecovery: Provider<Boolean>,
        @UserSessionBinding("pendingConversationCleanup")
        pendingConversationCleanup: Provider<PendingConversationCleanup>,
        @UserSessionBinding("cryptoTransactionProvider")
        cryptoTransactionProvider: Provider<CryptoTransactionProvider>,
        @UserSessionBinding("eventBatchProcessor")
        eventBatchProcessor: Provider<EventBatchProcessor>,
        @UserSessionBinding("clientRepository")
        clientRepository: Provider<ClientRepository>,
        @UserSessionBinding("legalHoldHandler")
        legalHoldHandler: Provider<LegalHoldHandlerImpl>,
        @UserSessionBinding("userScopedLogger")
        userScopedLogger: Provider<KaliumLogger>,
        @UserSessionBinding("userStorage")
        userStorage: Provider<UserStorage>,
        @UserSessionBinding("logout")
        logout: Provider<LogoutUseCase>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
    ): UserSessionIncrementalWorkerFactory =
        UserSessionIncrementalWorkerFactory(
            enableDurableMLSReceiveRecovery,
            pendingConversationCleanup,
            cryptoTransactionProvider,
            eventBatchProcessor,
            clientRepository,
            legalHoldHandler,
            userScopedLogger,
            userStorage,
            logout,
            userId,
        )

}
