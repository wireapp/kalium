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
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapperImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.EventRepository
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
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenUseCase
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenToAPIUseCaseImpl
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
    private val proteusClientProvider: ProteusClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val userRepository: UserRepository,
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
    logger: KaliumLogger,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    val establishSession: EstablishSessionUseCase
        get() = EstablishSessionUseCaseImpl(sessionEstablisher)

    val breakSession: BreakSessionUseCase
        get() = BreakSessionUseCaseImpl(proteusClientProvider)

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
            userRepository,
            currentClientIdProvider,
            slowSyncRepository,
            messageSender
        )

    val disableEventProcessing: DisableEventProcessingUseCase
        get() = DisableEventProcessingUseCaseImpl(
            eventProcessor = eventProcessor
        )

    val synchronizeExternalData: SynchronizeExternalDataUseCase
        get() = SynchronizeExternalDataUseCaseImpl(
            eventRepository = eventRepository,
            eventProcessor = eventProcessor
        )

    private val messageSendFailureHandler: MessageSendFailureHandler
        get() = MessageSendFailureHandlerImpl(
            userRepository,
            clientRepository,
            clientRemoteRepository,
            messageRepository,
            messageSendingScheduler,
            conversationRepository,
        )

    private val sessionEstablisher: SessionEstablisher
        get() = SessionEstablisherImpl(proteusClientProvider, preKeyRepository)

    private val protoContentMapper: ProtoContentMapper
        get() = ProtoContentMapperImpl(selfUserId = userId)

    private val messageEnvelopeCreator: MessageEnvelopeCreator
        get() = MessageEnvelopeCreatorImpl(
            conversationRepository = conversationRepository,
            legalHoldStatusMapper = LegalHoldStatusMapperImpl,
            proteusClientProvider = proteusClientProvider,
            selfUserId = userId,
            protoContentMapper = protoContentMapper
        )

    private val mlsMessageCreator: MLSMessageCreator
        get() = MLSMessageCreatorImpl(
            conversationRepository = conversationRepository,
            mlsClientProvider = mlsClientProvider,
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
            selfConversationIdProvider = selfConversationIdProvider
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
}
