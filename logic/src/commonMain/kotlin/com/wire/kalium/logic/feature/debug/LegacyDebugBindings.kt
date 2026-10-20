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

// Debug composition uses the same network event port as the existing public debug API.
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")
@file:OptIn(DebugKaliumApi::class, InternalKaliumApi::class)

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.X509CredentialAcquisitionConfigProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.event.EventGenerator
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserLifetime
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.message.MessageSendingInterceptor
import com.wire.kalium.logic.feature.message.MessageSendingInterceptorImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.ProteusRecipientsDataSource
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.SyncedSendingSession
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandlerImpl
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenToAPIUseCaseImpl
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.messaging.sending.MLSMessageCreator
import com.wire.kalium.messaging.sending.MLSMessageCreatorImpl
import com.wire.kalium.messaging.sending.MessageEnvelopeCreator
import com.wire.kalium.messaging.sending.MessageEnvelopeCreatorImpl
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSendFailureHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageSenderImpl
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Separates cached facade captures from other user factories and same-typed captures. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyDebugBinding(val value: String)

/**
 * Adapts session accessors without reading them eagerly. Each capture is retained independently.
 * These captures are distinct from MessageScope, including repository wrappers, encoder and self-deletion state.
 *
 * Session adapter getters preserve the original constructor argument mapping. Most names map
 * to matching UserSessionScope accessors, with these exceptions:
 * `joinExistingMLSConversationUseCaseProvider -> { joinExistingMLSConversationUseCase }`;
 * `currentClientIdProvider -> clientIdProvider`; `scope -> this@UserSessionScope`;
 * `mlsMissingUsersMessageRejectionHandlerProvider -> mlsMissingUsersRejectionHandlerProvider`;
 * `resetMLSConversationUseCase -> resetMlsConversation`; `transactionProvider -> cryptoTransactionProvider`;
 * `refillKeyPackagesUseCase -> kaliumUserGraph.clientRefillKeyPackages` (old `client.refillKeyPackages` capture);
 * `logger -> userScopedLogger`; `dispatcher -> KaliumDispatcherImpl` (the old constructor default).
 * Message/conversation repositories are separate session factory captures, never aliases of the
 * MessageScope or ConversationScope captures. Startup requests `debugRepairFaultyRemovalKeysUseCase`
 * directly; it must share that retained binding with this facade.
 */
internal interface LegacyDebugDependencies {
    val messageRepository: MessageRepository
    val conversationRepository: ConversationRepository
    val mlsConversationRepository: MLSConversationRepository
    val joinExistingMLSConversationUseCaseProvider: () -> JoinExistingMLSConversationUseCase
    val clientRepository: ClientRepository
    val clientRemoteRepository: ClientRemoteRepository
    val currentClientIdProvider: CurrentClientIdProvider
    val preKeyRepository: PreKeyRepository
    val userRepository: UserRepository
    val featureConfigRepository: FeatureConfigRepository
    val userId: UserId
    val assetRepository: AssetRepository
    val eventRepository: EventRepository
    val syncManager: SyncManager
    val slowSyncRepository: SlowSyncRepository
    val messageSendingScheduler: MessageSendingScheduler
    val selfConversationIdProvider: SelfConversationIdProvider
    val staleEpochVerifier: StaleEpochVerifier
    val eventProcessor: EventProcessor
    val legalHoldHandler: LegalHoldHandler
    val notificationTokenRepository: NotificationTokenRepository
    val scope: CoroutineScope
    val userStorage: UserStorage
    val mlsMissingUsersMessageRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler
    val x509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider
    val fetchConversationUseCase: FetchConversationUseCase
    val resetMLSConversationUseCase: ResetMLSConversationUseCase
    val transactionProvider: CryptoTransactionProvider
    val refillKeyPackagesUseCase: RefillKeyPackagesUseCase
    val certificateRevocationListRepository: CertificateRevocationListRepository
    val logger: KaliumLogger
    val dispatcher: KaliumDispatcher
}

/** Keeps an original function as a port value instead of Metro deferred injection. */
internal class LegacyDebugCapture<out T>(val value: T)

/** Individual entry points for the compatibility facade and user runtime. */
internal interface LegacyDebugApi {
    @get:LegacyDebugBinding("messageRepository")
    val debugMessageRepository: MessageRepository

    @get:LegacyDebugBinding("conversationRepository")
    val debugConversationRepository: ConversationRepository

    @get:LegacyDebugBinding("mlsConversationRepository")
    val debugMlsConversationRepository: MLSConversationRepository

    @get:LegacyDebugBinding("joinExistingMLSConversationUseCaseProvider")
    val debugJoinExistingMLSConversationUseCaseProvider: LegacyDebugCapture<() -> JoinExistingMLSConversationUseCase>

    @get:LegacyDebugBinding("clientRepository")
    val debugClientRepository: ClientRepository

    @get:LegacyDebugBinding("clientRemoteRepository")
    val debugClientRemoteRepository: ClientRemoteRepository

    @get:LegacyDebugBinding("currentClientIdProvider")
    val debugCurrentClientIdProvider: CurrentClientIdProvider

    @get:LegacyDebugBinding("preKeyRepository")
    val debugPreKeyRepository: PreKeyRepository

    @get:LegacyDebugBinding("userRepository")
    val debugUserRepository: UserRepository

    @get:LegacyDebugBinding("featureConfigRepository")
    val debugFeatureConfigRepository: FeatureConfigRepository

    @get:LegacyDebugBinding("userId")
    val debugUserId: UserId

    @get:LegacyDebugBinding("assetRepository")
    val debugAssetRepository: AssetRepository

    @get:LegacyDebugBinding("eventRepository")
    val debugEventRepository: EventRepository

    @get:LegacyDebugBinding("syncManager")
    val debugSyncManager: SyncManager

    @get:LegacyDebugBinding("slowSyncRepository")
    val debugSlowSyncRepository: SlowSyncRepository

    @get:LegacyDebugBinding("messageSendingScheduler")
    val debugMessageSendingScheduler: MessageSendingScheduler

    @get:LegacyDebugBinding("selfConversationIdProvider")
    val debugSelfConversationIdProvider: SelfConversationIdProvider

    @get:LegacyDebugBinding("staleEpochVerifier")
    val debugStaleEpochVerifier: StaleEpochVerifier

    @get:LegacyDebugBinding("eventProcessor")
    val debugEventProcessor: EventProcessor

    @get:LegacyDebugBinding("legalHoldHandler")
    val debugLegalHoldHandler: LegalHoldHandler

    @get:LegacyDebugBinding("notificationTokenRepository")
    val debugNotificationTokenRepository: NotificationTokenRepository

    @get:LegacyDebugBinding("scope")
    val debugScope: CoroutineScope

    @get:LegacyDebugBinding("userStorage")
    val debugUserStorage: UserStorage

    @get:LegacyDebugBinding("mlsMissingUsersMessageRejectionHandlerProvider")
    val debugMlsMissingUsersMessageRejectionHandlerProvider: LegacyDebugCapture<() -> MLSMissingUsersMessageRejectionHandler>

    @get:LegacyDebugBinding("x509CredentialAcquisitionConfigProvider")
    val debugX509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider

    @get:LegacyDebugBinding("fetchConversationUseCase")
    val debugFetchConversationUseCase: FetchConversationUseCase

    @get:LegacyDebugBinding("resetMLSConversationUseCase")
    val debugResetMLSConversationUseCase: ResetMLSConversationUseCase

    @get:LegacyDebugBinding("transactionProvider")
    val debugTransactionProvider: CryptoTransactionProvider

    @get:LegacyDebugBinding("refillKeyPackagesUseCase")
    val debugRefillKeyPackagesUseCase: RefillKeyPackagesUseCase

    @get:LegacyDebugBinding("certificateRevocationListRepository")
    val debugCertificateRevocationListRepository: CertificateRevocationListRepository

    @get:LegacyDebugBinding("logger")
    val debugLogger: KaliumLogger

    @get:LegacyDebugBinding("dispatcher")
    val debugDispatcher: KaliumDispatcher

    @get:LegacyDebugBinding("getSqlCipherVersion")
    val debugGetSqlCipherVersion: GetSqlCipherVersionUseCase

    @get:LegacyDebugBinding("establishSession")
    val debugEstablishSession: EstablishSessionUseCase

    @get:LegacyDebugBinding("breakSession")
    val debugBreakSession: BreakSessionUseCase

    @get:LegacyDebugBinding("sendBrokenAssetMessage")
    val debugSendBrokenAssetMessage: SendBrokenAssetMessageUseCase

    @get:LegacyDebugBinding("sendConfirmation")
    val debugSendConfirmation: SendConfirmationUseCase

    @get:LegacyDebugBinding("disableEventProcessing")
    val debugDisableEventProcessing: DisableEventProcessingUseCase

    @get:LegacyDebugBinding("getConversationEpochFromCC")
    val debugGetConversationEpochFromCC: GetConversationEpochFromCCUseCase

    @get:LegacyDebugBinding("synchronizeExternalData")
    val debugSynchronizeExternalData: SynchronizeExternalDataUseCase

    @get:LegacyDebugBinding("messageSendFailureHandler")
    val debugMessageSendFailureHandler: MessageSendFailureHandler

    @get:LegacyDebugBinding("sessionEstablisher")
    val debugSessionEstablisher: SessionEstablisher

    @get:LegacyDebugBinding("protoContentMapper")
    val debugProtoContentMapper: ProtoContentMapper

    @get:LegacyDebugBinding("messageEnvelopeCreator")
    val debugMessageEnvelopeCreator: MessageEnvelopeCreator

    @get:LegacyDebugBinding("mlsMessageCreator")
    val debugMlsMessageCreator: MLSMessageCreator

    @get:LegacyDebugBinding("messageContentEncoder")
    val debugMessageContentEncoder: MessageContentEncoder

    @get:LegacyDebugBinding("messageSendingInterceptor")
    val debugMessageSendingInterceptor: MessageSendingInterceptor

    @get:LegacyDebugBinding("messageSender")
    val debugMessageSender: MessageSender

    @get:LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsReceiver")
    val debugDeleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl

    @get:LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsSender")
    val debugDeleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl

    @get:LegacyDebugBinding("ephemeralMessageDeletionHandler")
    val debugEphemeralMessageDeletionHandler: EphemeralMessageDeletionHandlerImpl

    @get:LegacyDebugBinding("sendFCMTokenToServer")
    val debugSendFCMTokenToServer: SendFCMTokenUseCase

    @get:LegacyDebugBinding("changeProfiling")
    val debugChangeProfiling: ChangeProfilingUseCase

    @get:LegacyDebugBinding("observeDatabaseLoggerState")
    val debugObserveDatabaseLoggerState: ObserveDatabaseLoggerStateUseCase

    @get:LegacyDebugBinding("optimizeDatabase")
    val debugOptimizeDatabase: OptimizeDatabaseUseCase

    @get:LegacyDebugBinding("debugFeedConversationUseCase")
    val debugDebugFeedConversationUseCase: DebugFeedConversationUseCase

    @get:LegacyDebugBinding("getFeatureConfig")
    val debugGetFeatureConfig: GetFeatureConfigUseCase

    @get:LegacyDebugBinding("getCryptoServiceReport")
    val debugGetCryptoServiceReport: GetCryptoServiceReportUseCase

    @get:LegacyDebugBinding("getDebugE2EICertificateExpiration")
    val debugGetDebugE2EICertificateExpiration: GetDebugE2EICertificateExpirationUseCase

    @get:LegacyDebugBinding("setDebugE2EICertificateExpiration")
    val debugSetDebugE2EICertificateExpiration: SetDebugE2EICertificateExpirationUseCase

    @get:LegacyDebugBinding("observeDebugCRLExpirationAfterOneMinute")
    val debugObserveDebugCRLExpirationAfterOneMinute: ObserveDebugCRLExpirationAfterOneMinuteUseCase

    @get:LegacyDebugBinding("setDebugCRLExpirationAfterOneMinute")
    val debugSetDebugCRLExpirationAfterOneMinute: SetDebugCRLExpirationAfterOneMinuteUseCase

    @get:LegacyDebugBinding("repairFaultyRemovalKeysUseCase")
    val debugRepairFaultyRemovalKeysUseCase: RepairFaultyRemovalKeysUseCase

    @get:LegacyDebugBinding("getConversationCryptoStats")
    val debugGetConversationCryptoStats: GetConversationCryptoStatsUseCase

    @get:LegacyDebugBinding("refillKeyPackages")
    val debugRefillKeyPackages: DebugRefillKeyPackages

    @get:LegacyDebugBinding("generateEvents")
    val debugGenerateEvents: DebugGenerateEvents
}

internal fun interface DebugRefillKeyPackages {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal fun interface DebugGenerateEvents {
    suspend operator fun invoke(
        targetUserId: UserId,
        targetClientId: ClientId,
        conversationId: ConversationId,
        limit: Int,
    ): Flow<EventResponse>
}

/**
 * Mixed into the user graph: captures and formerly retained values use the existing user lifetime.
 * All original factory getters remain unscoped. No feature graph or broad facade constructor is used.
 */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass") // Declarative individual bindings.
internal interface LegacyDebugBindings : LegacyDebugApi {
    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("messageRepository")
    fun provideDebugMessageRepository(dependencies: LegacyDebugDependencies): MessageRepository =
        dependencies.messageRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("conversationRepository")
    fun provideDebugConversationRepository(dependencies: LegacyDebugDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("mlsConversationRepository")
    fun provideDebugMlsConversationRepository(dependencies: LegacyDebugDependencies): MLSConversationRepository =
        dependencies.mlsConversationRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("joinExistingMLSConversationUseCaseProvider")
    fun provideDebugJoinExistingMLSConversationUseCaseProvider(
        dependencies: LegacyDebugDependencies,
    ): LegacyDebugCapture<() -> JoinExistingMLSConversationUseCase> =
        LegacyDebugCapture(dependencies.joinExistingMLSConversationUseCaseProvider)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("clientRepository")
    fun provideDebugClientRepository(dependencies: LegacyDebugDependencies): ClientRepository =
        dependencies.clientRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("clientRemoteRepository")
    fun provideDebugClientRemoteRepository(dependencies: LegacyDebugDependencies): ClientRemoteRepository =
        dependencies.clientRemoteRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("currentClientIdProvider")
    fun provideDebugCurrentClientIdProvider(dependencies: LegacyDebugDependencies): CurrentClientIdProvider =
        dependencies.currentClientIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("preKeyRepository")
    fun provideDebugPreKeyRepository(dependencies: LegacyDebugDependencies): PreKeyRepository =
        dependencies.preKeyRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("userRepository")
    fun provideDebugUserRepository(dependencies: LegacyDebugDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("featureConfigRepository")
    fun provideDebugFeatureConfigRepository(dependencies: LegacyDebugDependencies): FeatureConfigRepository =
        dependencies.featureConfigRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("userId")
    fun provideDebugUserId(dependencies: LegacyDebugDependencies): UserId =
        dependencies.userId

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("assetRepository")
    fun provideDebugAssetRepository(dependencies: LegacyDebugDependencies): AssetRepository =
        dependencies.assetRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("eventRepository")
    fun provideDebugEventRepository(dependencies: LegacyDebugDependencies): EventRepository =
        dependencies.eventRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("syncManager")
    fun provideDebugSyncManager(dependencies: LegacyDebugDependencies): SyncManager =
        dependencies.syncManager

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("slowSyncRepository")
    fun provideDebugSlowSyncRepository(dependencies: LegacyDebugDependencies): SlowSyncRepository =
        dependencies.slowSyncRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("messageSendingScheduler")
    fun provideDebugMessageSendingScheduler(dependencies: LegacyDebugDependencies): MessageSendingScheduler =
        dependencies.messageSendingScheduler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("selfConversationIdProvider")
    fun provideDebugSelfConversationIdProvider(dependencies: LegacyDebugDependencies): SelfConversationIdProvider =
        dependencies.selfConversationIdProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("staleEpochVerifier")
    fun provideDebugStaleEpochVerifier(dependencies: LegacyDebugDependencies): StaleEpochVerifier =
        dependencies.staleEpochVerifier

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("eventProcessor")
    fun provideDebugEventProcessor(dependencies: LegacyDebugDependencies): EventProcessor =
        dependencies.eventProcessor

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("legalHoldHandler")
    fun provideDebugLegalHoldHandler(dependencies: LegacyDebugDependencies): LegalHoldHandler =
        dependencies.legalHoldHandler

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("notificationTokenRepository")
    fun provideDebugNotificationTokenRepository(dependencies: LegacyDebugDependencies): NotificationTokenRepository =
        dependencies.notificationTokenRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("scope")
    fun provideDebugScope(dependencies: LegacyDebugDependencies): CoroutineScope =
        dependencies.scope

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("userStorage")
    fun provideDebugUserStorage(dependencies: LegacyDebugDependencies): UserStorage =
        dependencies.userStorage

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("mlsMissingUsersMessageRejectionHandlerProvider")
    fun provideDebugMlsMissingUsersMessageRejectionHandlerProvider(
        dependencies: LegacyDebugDependencies,
    ): LegacyDebugCapture<() -> MLSMissingUsersMessageRejectionHandler> =
        LegacyDebugCapture(dependencies.mlsMissingUsersMessageRejectionHandlerProvider)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("x509CredentialAcquisitionConfigProvider")
    fun provideDebugX509CredentialAcquisitionConfigProvider(
        dependencies: LegacyDebugDependencies,
    ): X509CredentialAcquisitionConfigProvider =
        dependencies.x509CredentialAcquisitionConfigProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("fetchConversationUseCase")
    fun provideDebugFetchConversationUseCase(dependencies: LegacyDebugDependencies): FetchConversationUseCase =
        dependencies.fetchConversationUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("resetMLSConversationUseCase")
    fun provideDebugResetMLSConversationUseCase(dependencies: LegacyDebugDependencies): ResetMLSConversationUseCase =
        dependencies.resetMLSConversationUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("transactionProvider")
    fun provideDebugTransactionProvider(dependencies: LegacyDebugDependencies): CryptoTransactionProvider =
        dependencies.transactionProvider

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("refillKeyPackagesUseCase")
    fun provideDebugRefillKeyPackagesUseCase(dependencies: LegacyDebugDependencies): RefillKeyPackagesUseCase =
        dependencies.refillKeyPackagesUseCase

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("certificateRevocationListRepository")
    fun provideDebugCertificateRevocationListRepository(dependencies: LegacyDebugDependencies): CertificateRevocationListRepository =
        dependencies.certificateRevocationListRepository

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("logger")
    fun provideDebugLogger(dependencies: LegacyDebugDependencies): KaliumLogger =
        dependencies.logger

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("dispatcher")
    fun provideDebugDispatcher(dependencies: LegacyDebugDependencies): KaliumDispatcher =
        dependencies.dispatcher

    @Provides
    @LegacyDebugBinding("getSqlCipherVersion")
    fun provideDebugGetSqlCipherVersion(
        @LegacyDebugBinding("userStorage") userStorage: UserStorage,
    ): GetSqlCipherVersionUseCase = GetSqlCipherVersionUseCase(userStorage)

    @Provides
    @LegacyDebugBinding("establishSession")
    fun provideDebugEstablishSession(
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyDebugBinding("sessionEstablisher") sessionEstablisher: SessionEstablisher,
    ): EstablishSessionUseCase = EstablishSessionUseCaseImpl(sessionEstablisher, transactionProvider)

    @Provides
    @LegacyDebugBinding("breakSession")
    fun provideDebugBreakSession(
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): BreakSessionUseCase = BreakSessionUseCaseImpl(transactionProvider)

    @Provides
    @LegacyDebugBinding("sendBrokenAssetMessage")
    fun provideDebugSendBrokenAssetMessage(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyDebugBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyDebugBinding("messageSender") messageSender: MessageSender,
    ): SendBrokenAssetMessageUseCase = SendBrokenAssetMessageUseCaseImpl(
        currentClientIdProvider,
        assetRepository,
        userId,
        slowSyncRepository,
        messageSender,
        messageRepository
    )

    @Provides
    @LegacyDebugBinding("sendConfirmation")
    fun provideDebugSendConfirmation(
        @LegacyDebugBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
        @LegacyDebugBinding("messageSender") messageSender: MessageSender,
    ): SendConfirmationUseCase = SendConfirmationUseCase(
        currentClientIdProvider = currentClientIdProvider,
        slowSyncRepository = slowSyncRepository,
        messageSender = messageSender,
        selfUserId = userId,
    )

    @Provides
    @LegacyDebugBinding("disableEventProcessing")
    fun provideDebugDisableEventProcessing(
        @LegacyDebugBinding("eventProcessor") eventProcessor: EventProcessor,
    ): DisableEventProcessingUseCase = DisableEventProcessingUseCaseImpl(
        eventProcessor = eventProcessor
    )

    @Provides
    @LegacyDebugBinding("getConversationEpochFromCC")
    fun provideDebugGetConversationEpochFromCC(
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetConversationEpochFromCCUseCase = GetConversationEpochFromCCUseCaseImpl(
        conversationRepository = conversationRepository,
        transactionProvider = transactionProvider
    )

    @Provides
    @LegacyDebugBinding("synchronizeExternalData")
    fun provideDebugSynchronizeExternalData(
        @LegacyDebugBinding("eventRepository") eventRepository: EventRepository,
        @LegacyDebugBinding("eventProcessor") eventProcessor: EventProcessor,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): SynchronizeExternalDataUseCase = SynchronizeExternalDataUseCaseImpl(
        eventRepository = eventRepository,
        eventProcessor = eventProcessor,
        transactionProvider = transactionProvider
    )

    @Provides
    @LegacyDebugBinding("messageSendFailureHandler")
    fun provideDebugMessageSendFailureHandler(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyDebugBinding("clientRemoteRepository") clientRemoteRepository: ClientRemoteRepository,
        @LegacyDebugBinding("userRepository") userRepository: UserRepository,
        @LegacyDebugBinding("messageSendingScheduler") messageSendingScheduler: MessageSendingScheduler,
        @LegacyDebugBinding("fetchConversationUseCase") fetchConversationUseCase: FetchConversationUseCase,
    ): MessageSendFailureHandler = MessageSendFailureHandlerImpl(
        recipients = ProteusRecipientsDataSource(
            userRepository,
            clientRepository,
            clientRemoteRepository,
            fetchConversationUseCase
        ),
        updateMessageStatus = messageRepository::updateMessageStatus,
        scheduleResend = messageSendingScheduler::scheduleSendingOfPendingMessages
    )

    @Provides
    @LegacyDebugBinding("sessionEstablisher")
    fun provideDebugSessionEstablisher(
        @LegacyDebugBinding("preKeyRepository") preKeyRepository: PreKeyRepository,
    ): SessionEstablisher = SessionEstablisherImpl(preKeyRepository::establishSessions)

    @Provides
    @LegacyDebugBinding("protoContentMapper")
    fun provideDebugProtoContentMapper(
        @LegacyDebugBinding("userId") userId: UserId,
    ): ProtoContentMapper = ProtoContentMapperImpl(selfUserId = userId)

    @Provides
    @LegacyDebugBinding("messageEnvelopeCreator")
    fun provideDebugMessageEnvelopeCreator(
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("protoContentMapper") protoContentMapper: ProtoContentMapper,
    ): MessageEnvelopeCreator = MessageEnvelopeCreatorImpl(
        legalHoldStatus = { conversationRepository.observeLegalHoldStatus(it).first() },
        selfUserId = userId,
        protoContentMapper = protoContentMapper
    )

    @Provides
    @LegacyDebugBinding("mlsMessageCreator")
    fun provideDebugMlsMessageCreator(
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("mlsConversationRepository") mlsConversationRepository: MLSConversationRepository,
        @LegacyDebugBinding("joinExistingMLSConversationUseCaseProvider")
        joinExistingMLSConversationUseCaseProvider: LegacyDebugCapture<() -> JoinExistingMLSConversationUseCase>,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("protoContentMapper") protoContentMapper: ProtoContentMapper,
    ): MLSMessageCreator = MLSMessageCreatorImpl(
        legalHoldStatus = { conversationRepository.observeLegalHoldStatus(it).first() },
        commitPendingProposals = mlsConversationRepository::commitPendingProposals,
        joinConversation = { context, conversationId -> joinExistingMLSConversationUseCaseProvider.value()(context, conversationId) },
        encrypt = mlsConversationRepository::encryptMessage,
        selfUserId = userId,
        protoContentMapper = protoContentMapper
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("messageContentEncoder")
    fun provideDebugMessageContentEncoder(): MessageContentEncoder = MessageContentEncoder()

    @Provides
    @LegacyDebugBinding("messageSendingInterceptor")
    fun provideDebugMessageSendingInterceptor(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("messageContentEncoder") messageContentEncoder: MessageContentEncoder,
    ): MessageSendingInterceptor = MessageSendingInterceptorImpl(messageContentEncoder, messageRepository)

    // Self-deletion retains a receiver and its sender; enqueueing resolves the retained handler later.
    @Provides
    @LegacyDebugBinding("messageSender")
    fun provideDebugMessageSender(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("userRepository") userRepository: UserRepository,
        @LegacyDebugBinding("syncManager") syncManager: SyncManager,
        @LegacyDebugBinding("staleEpochVerifier") staleEpochVerifier: StaleEpochVerifier,
        @LegacyDebugBinding("legalHoldHandler") legalHoldHandler: LegalHoldHandler,
        @LegacyDebugBinding("scope") scope: CoroutineScope,
        @LegacyDebugBinding("mlsMissingUsersMessageRejectionHandlerProvider")
        mlsMissingUsersMessageRejectionHandlerProvider: LegacyDebugCapture<() -> MLSMissingUsersMessageRejectionHandler>,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyDebugBinding("messageSendFailureHandler") messageSendFailureHandler: MessageSendFailureHandler,
        @LegacyDebugBinding("sessionEstablisher") sessionEstablisher: SessionEstablisher,
        @LegacyDebugBinding("messageEnvelopeCreator") messageEnvelopeCreator: MessageEnvelopeCreator,
        @LegacyDebugBinding("mlsMessageCreator") mlsMessageCreator: MLSMessageCreator,
        @LegacyDebugBinding("messageSendingInterceptor") messageSendingInterceptor: MessageSendingInterceptor,
        @LegacyDebugBinding("ephemeralMessageDeletionHandler")
        ephemeralMessageDeletionHandler: Provider<EphemeralMessageDeletionHandlerImpl>,
    ): MessageSender = MessageSenderImpl(
        messages = messageRepository,
        transport = messageRepository,
        conversations = conversationRepository,
        session = SyncedSendingSession(syncManager, transactionProvider),
        messageSendFailureHandler = messageSendFailureHandler,
        sessionEstablisher = sessionEstablisher,
        messageEnvelopeCreator = messageEnvelopeCreator,
        mlsMessageCreator = mlsMessageCreator,
        prepareMessage = messageSendingInterceptor::prepareMessage,
        legalHoldEnabledAfter = legalHoldHandler::handleMessageSendFailure,
        verifyEpoch = { context, conversationId -> staleEpochVerifier.verifyEpoch(context, conversationId) },
        handleGroupOutOfSync = mlsMissingUsersMessageRejectionHandlerProvider.value()::handle,
        broadcastRecipients = userRepository::getAllRecipients,
        enqueueSelfDeletion = { message, expirationData ->
            ephemeralMessageDeletionHandler().enqueueSelfDeletion(message, expirationData)
        },
        scope = scope
    )

    @Provides
    @LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsReceiver")
    fun provideDebugDeleteEphemeralMessageForSelfUserAsReceiver(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("assetRepository") assetRepository: AssetRepository,
        @LegacyDebugBinding("syncManager") syncManager: SyncManager,
        @LegacyDebugBinding("selfConversationIdProvider") selfConversationIdProvider: SelfConversationIdProvider,
        @LegacyDebugBinding("messageSender") messageSender: MessageSender,
    ): DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl = DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
        messageRepository = messageRepository,
        assetRepository = assetRepository,
        currentClientIdProvider = currentClientIdProvider,
        messageSender = messageSender,
        selfUserId = userId,
        selfConversationIdProvider = selfConversationIdProvider,
        syncManager = syncManager,
    )

    @Provides
    @LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsSender")
    fun provideDebugDeleteEphemeralMessageForSelfUserAsSender(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("assetRepository") assetRepository: AssetRepository,
    ): DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl = DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(
        messageRepository = messageRepository,
        assetRepository = assetRepository,
    )

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("ephemeralMessageDeletionHandler")
    fun provideDebugEphemeralMessageDeletionHandler(
        @LegacyDebugBinding("messageRepository") messageRepository: MessageRepository,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("scope") scope: CoroutineScope,
        @LegacyDebugBinding("logger") logger: KaliumLogger,
        @LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsReceiver")
        deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl,
        @LegacyDebugBinding("deleteEphemeralMessageForSelfUserAsSender")
        deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl,
    ): EphemeralMessageDeletionHandlerImpl = EphemeralMessageDeletionHandlerImpl(
        userSessionCoroutineScope = scope,
        messageRepository = messageRepository,
        deleteEphemeralMessageForSelfUserAsReceiver = deleteEphemeralMessageForSelfUserAsReceiver,
        deleteEphemeralMessageForSelfUserAsSender = deleteEphemeralMessageForSelfUserAsSender,
        selfUserId = userId,
        kaliumLogger = logger
    )

    @Provides
    @LegacyDebugBinding("sendFCMTokenToServer")
    fun provideDebugSendFCMTokenToServer(
        @LegacyDebugBinding("clientRepository") clientRepository: ClientRepository,
        @LegacyDebugBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyDebugBinding("notificationTokenRepository") notificationTokenRepository: NotificationTokenRepository,
    ): SendFCMTokenUseCase = SendFCMTokenToAPIUseCaseImpl(
        currentClientIdProvider,
        clientRepository,
        notificationTokenRepository,
    )

    @Provides
    @LegacyDebugBinding("changeProfiling")
    fun provideDebugChangeProfiling(
        @LegacyDebugBinding("userStorage") userStorage: UserStorage,
    ): ChangeProfilingUseCase = ChangeProfilingUseCase(userStorage)

    @Provides
    @LegacyDebugBinding("observeDatabaseLoggerState")
    fun provideDebugObserveDatabaseLoggerState(
        @LegacyDebugBinding("userStorage") userStorage: UserStorage,
    ): ObserveDatabaseLoggerStateUseCase = ObserveDatabaseLoggerStateUseCase(userStorage)

    @Provides
    @LegacyDebugBinding("optimizeDatabase")
    fun provideDebugOptimizeDatabase(
        @LegacyDebugBinding("userStorage") userStorage: UserStorage,
    ): OptimizeDatabaseUseCase = OptimizeDatabaseUseCaseImpl(userStorage.database.databaseOptimizer)

    @Provides
    @LegacyDebugBinding("debugFeedConversationUseCase")
    fun provideDebugDebugFeedConversationUseCase(
        @LegacyDebugBinding("userStorage") userStorage: UserStorage,
    ): DebugFeedConversationUseCase = DebugFeedConversationUseCaseImpl(
        userStorage.database.messagesFeeder,
        userStorage.database.reactionFeeder,
        userStorage.database.unreadEventsFeeder,
        userStorage.database.mentionsFeeder,
    )

    @Provides
    @LegacyDebugBinding("getFeatureConfig")
    fun provideDebugGetFeatureConfig(
        @LegacyDebugBinding("featureConfigRepository") featureConfigRepository: FeatureConfigRepository,
    ): GetFeatureConfigUseCase = GetFeatureConfigUseCaseImpl(featureConfigRepository)

    @Provides
    @LegacyDebugBinding("getCryptoServiceReport")
    fun provideDebugGetCryptoServiceReport(
        @LegacyDebugBinding("dispatcher") dispatcher: KaliumDispatcher,
    ): GetCryptoServiceReportUseCase = GetCryptoServiceReportUseCaseImpl(dispatcher)

    @Provides
    @LegacyDebugBinding("getDebugE2EICertificateExpiration")
    fun provideDebugGetDebugE2EICertificateExpiration(
        @LegacyDebugBinding("x509CredentialAcquisitionConfigProvider")
        x509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider,
    ): GetDebugE2EICertificateExpirationUseCase = GetDebugE2EICertificateExpirationUseCaseImpl(x509CredentialAcquisitionConfigProvider)

    @Provides
    @LegacyDebugBinding("setDebugE2EICertificateExpiration")
    fun provideDebugSetDebugE2EICertificateExpiration(
        @LegacyDebugBinding("x509CredentialAcquisitionConfigProvider")
        x509CredentialAcquisitionConfigProvider: X509CredentialAcquisitionConfigProvider,
    ): SetDebugE2EICertificateExpirationUseCase = SetDebugE2EICertificateExpirationUseCaseImpl(x509CredentialAcquisitionConfigProvider)

    @Provides
    @LegacyDebugBinding("observeDebugCRLExpirationAfterOneMinute")
    fun provideDebugObserveDebugCRLExpirationAfterOneMinute(
        @LegacyDebugBinding("certificateRevocationListRepository") certificateRevocationListRepository: CertificateRevocationListRepository,
    ): ObserveDebugCRLExpirationAfterOneMinuteUseCase =
        ObserveDebugCRLExpirationAfterOneMinuteUseCaseImpl(certificateRevocationListRepository)

    @Provides
    @LegacyDebugBinding("setDebugCRLExpirationAfterOneMinute")
    fun provideDebugSetDebugCRLExpirationAfterOneMinute(
        @LegacyDebugBinding("certificateRevocationListRepository") certificateRevocationListRepository: CertificateRevocationListRepository,
    ): SetDebugCRLExpirationAfterOneMinuteUseCase = SetDebugCRLExpirationAfterOneMinuteUseCaseImpl(certificateRevocationListRepository)

    @Provides
    @SingleIn(UserLifetime::class)
    @LegacyDebugBinding("repairFaultyRemovalKeysUseCase")
    fun provideDebugRepairFaultyRemovalKeysUseCase(
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("resetMLSConversationUseCase") resetMLSConversationUseCase: ResetMLSConversationUseCase,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): RepairFaultyRemovalKeysUseCase = RepairFaultyRemovalKeysUseCaseImpl(
        selfUserId = userId,
        conversationRepository = conversationRepository,
        resetMLSConversation = resetMLSConversationUseCase,
        transactionProvider = transactionProvider
    )

    @Provides
    @LegacyDebugBinding("getConversationCryptoStats")
    fun provideDebugGetConversationCryptoStats(
        @LegacyDebugBinding("conversationRepository") conversationRepository: ConversationRepository,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): GetConversationCryptoStatsUseCase = GetConversationCryptoStatsUseCaseImpl(
        conversationRepository = conversationRepository,
        transactionProvider = transactionProvider
    )

    @Provides
    @LegacyDebugBinding("refillKeyPackages")
    fun provideDebugRefillKeyPackages(
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
        @LegacyDebugBinding("refillKeyPackagesUseCase") refillKeyPackagesUseCase: RefillKeyPackagesUseCase,
    ): DebugRefillKeyPackages = DebugRefillKeyPackages {
        transactionProvider.transaction { transactionContext ->
            transactionContext.wrapInMLSContext { mlsContext ->
                when (val result = refillKeyPackagesUseCase(mlsContext)) {
                    is RefillKeyPackagesResult.Success -> Either.Right(Unit)
                    is RefillKeyPackagesResult.Failure -> Either.Left(result.failure)
                }
            }
        }
    }

    @Provides
    @LegacyDebugBinding("generateEvents")
    @Suppress("TooGenericExceptionThrown") // Preserve the existing debug API failure when no client ID is available.
    fun provideDebugGenerateEvents(
        @LegacyDebugBinding("currentClientIdProvider") currentClientIdProvider: CurrentClientIdProvider,
        @LegacyDebugBinding("userId") userId: UserId,
        @LegacyDebugBinding("transactionProvider") transactionProvider: CryptoTransactionProvider,
    ): DebugGenerateEvents = DebugGenerateEvents { targetUserId, targetClientId, conversationId, limit ->
        val selfClientId = currentClientIdProvider().getOrNull() ?: throw RuntimeException("No current client ID available")
        val generator = EventGenerator(
            selfClient = QualifiedClientID(clientId = selfClientId, userId = userId),
            targetClient = QualifiedClientID(clientId = targetClientId, userId = targetUserId),
        )
        generator.generateEvents(transactionProvider, limit, conversationId)
    }
}
