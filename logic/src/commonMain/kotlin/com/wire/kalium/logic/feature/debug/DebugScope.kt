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

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapperImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserStorage
import com.wire.kalium.logic.feature.client.UpdateSelfClientCapabilityToConsumableNotificationsUseCase
import com.wire.kalium.logic.feature.message.MLSMessageCreator
import com.wire.kalium.logic.feature.message.MLSMessageCreatorImpl
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreator
import com.wire.kalium.logic.feature.message.MessageEnvelopeCreatorImpl
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSendFailureHandlerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageSenderImpl
import com.wire.kalium.logic.feature.message.MessageSendingInterceptor
import com.wire.kalium.logic.feature.message.MessageSendingInterceptorImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandlerImpl
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenToAPIUseCaseImpl
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenUseCase
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope

/*
 * This scope can be used to test client behaviour. Debug functions are not needed for normal client activity.
 */
@Suppress("LongParameterList")
class DebugScope internal constructor(
    internal val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val clientRepository: ClientRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val preKeyRepository: PreKeyRepository,
    private val userRepository: UserRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val userId: UserId,
    private val assetRepository: AssetRepository,
    private val eventRepository: EventRepository,
    private val syncManager: SyncManager,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSendingScheduler: MessageSendingScheduler,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val staleEpochVerifier: StaleEpochVerifier,
    private val eventProcessor: EventProcessor,
    private val legalHoldHandler: LegalHoldHandler,
    private val notificationTokenRepository: NotificationTokenRepository,
    private val scope: CoroutineScope,
    private val userStorage: UserStorage,
    private val updateSelfClientCapabilityToConsumableNotifications:
    UpdateSelfClientCapabilityToConsumableNotificationsUseCase,
    private val selfServerConfig: SelfServerConfigUseCase,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    logger: KaliumLogger,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    val establishSession: EstablishSessionUseCase
        get() = EstablishSessionUseCaseImpl(sessionEstablisher, transactionProvider)

    val breakSession: BreakSessionUseCase
        get() = BreakSessionUseCaseImpl(transactionProvider)

    val sendBrokenAssetMessage: SendBrokenAssetMessageUseCase
        get() = SendBrokenAssetMessageUseCaseImpl(
            currentClientIdProvider,
            assetRepository,
            userId,
            slowSyncRepository,
            messageSender,
            messageRepository
        )

    val sendConfirmation: SendConfirmationUseCase
        get() = SendConfirmationUseCase(
            currentClientIdProvider = currentClientIdProvider,
            slowSyncRepository = slowSyncRepository,
            messageSender = messageSender,
            selfUserId = userId,
        )

    val disableEventProcessing: DisableEventProcessingUseCase
        get() = DisableEventProcessingUseCaseImpl(
            eventProcessor = eventProcessor
        )

    val synchronizeExternalData: SynchronizeExternalDataUseCase
        get() = SynchronizeExternalDataUseCaseImpl(
            eventRepository = eventRepository,
            eventProcessor = eventProcessor,
            transactionProvider = transactionProvider
        )

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(
            userRepository,
            clientRepository,
            clientRemoteRepository,
            messageRepository,
            messageSendingScheduler,
            fetchConversationUseCase
        )

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(preKeyRepository)

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl(selfUserId = userId)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(
            conversationRepository = conversationRepository,
            legalHoldStatusMapper = LegalHoldStatusMapperImpl,
            selfUserId = userId,
            protoContentMapper = protoContentMapper
        )

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(
            conversationRepository = conversationRepository,
            legalHoldStatusMapper = LegalHoldStatusMapperImpl,
            selfUserId = userId,
            protoContentMapper = protoContentMapper
        )

    private val messageContentEncoder = MessageContentEncoder()
    private val messageSendingInterceptor: MessageSendingInterceptor
        get() = MessageSendingInterceptorImpl(messageContentEncoder, messageRepository)

    internal val messageSender: MessageSender
        get() = MessageSenderImpl(
            messageRepository,
            conversationRepository,
            mlsConversationRepository,
            syncManager,
            messageSendFailureHandler,
            legalHoldHandler,
            sessionEstablisher,
            messageEnvelopeCreator,
            mlsMessageCreator,
            messageSendingInterceptor,
            userRepository,
            staleEpochVerifier,
            transactionProvider,
            { message, expirationData ->
                ephemeralMessageDeletionHandler.enqueueSelfDeletion(
                    message,
                    expirationData
                )
            },
            scope
        )

    private val deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
        get() = DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository,
            currentClientIdProvider = currentClientIdProvider,
            messageSender = messageSender,
            selfUserId = userId,
            selfConversationIdProvider = selfConversationIdProvider,
            syncManager = syncManager,
        )

    private val deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl
        get() = DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository,
        )

    private val ephemeralMessageDeletionHandler =
        EphemeralMessageDeletionHandlerImpl(
            userSessionCoroutineScope = scope,
            messageRepository = messageRepository,
            deleteEphemeralMessageForSelfUserAsReceiver = deleteEphemeralMessageForSelfUserAsReceiver,
            deleteEphemeralMessageForSelfUserAsSender = deleteEphemeralMessageForSelfUserAsSender,
            selfUserId = userId,
            kaliumLogger = logger
        )

    val sendFCMTokenToServer: SendFCMTokenUseCase
        get() = SendFCMTokenToAPIUseCaseImpl(
            currentClientIdProvider,
            clientRepository,
            notificationTokenRepository,
        )

    val changeProfiling: ChangeProfilingUseCase get() = ChangeProfilingUseCase(userStorage)

    val observeDatabaseLoggerState get() = ObserveDatabaseLoggerStateUseCase(userStorage)

    val optimizeDatabase get(): OptimizeDatabaseUseCase = OptimizeDatabaseUseCaseImpl(userStorage.database.databaseOptimizer)

    val startUsingAsyncNotifications: StartUsingAsyncNotificationsUseCase
        get() = StartUsingAsyncNotificationsUseCaseImpl(selfServerConfig, updateSelfClientCapabilityToConsumableNotifications)

    val observeIsConsumableNotificationsEnabled: ObserveIsConsumableNotificationsEnabledUseCase
        get() = ObserveIsConsumableNotificationsEnabledUseCaseImpl(clientRepository)

    val getFeatureConfig: GetFeatureConfigUseCase
        get() = GetFeatureConfigUseCaseImpl(featureConfigRepository)
}
