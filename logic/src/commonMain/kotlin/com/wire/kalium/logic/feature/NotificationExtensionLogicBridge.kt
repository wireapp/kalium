/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:Suppress("TooManyFunctions", "LongParameterList", "LargeClass")

package com.wire.kalium.logic.notificationextension

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messagecontent.DecodedProtobufContent
import com.wire.kalium.messagecontent.NotificationContent
import com.wire.kalium.messagecontent.NotificationContentExtractionResult
import com.wire.kalium.messagecontent.NotificationContentExtractor
import com.wire.kalium.messagecontent.NotificationContentExtractorImpl
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoder
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoderImpl
import com.wire.kalium.messaging.receiving.DecodedMessageContent
import com.wire.kalium.messaging.receiving.MessageContentDecoder
import com.wire.kalium.messaging.receiving.MessageContentResolution
import com.wire.kalium.messaging.receiving.MessageContentResolver
import com.wire.kalium.messaging.receiving.MessageContentResolverImpl
import com.wire.kalium.messaging.receiving.MlsEncryptedMessage
import com.wire.kalium.messaging.receiving.MlsMessageDecryptor
import com.wire.kalium.messaging.receiving.MlsMessageDecryptorImpl
import com.wire.kalium.messaging.receiving.ProteusEncryptedMessage
import com.wire.kalium.messaging.receiving.ProteusMessageDecryptor
import com.wire.kalium.messaging.receiving.ProteusMessageDecryptorImpl
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.base.authenticated.notification.EventAcknowledgeResult
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlin.concurrent.atomics.AtomicInt
import kotlin.io.encoding.Base64

/**
 * Signals that bridge construction failed and at least one acquired resource could not be closed.
 *
 * Callers sharing account state with another process must retain their process lock when this is
 * thrown. Ordinary construction failures are not represented by this type because their rollback
 * completed safely.
 */
public class NotificationExtensionLogicBridgeUnsafeTeardownException(
    cause: Throwable
) : IllegalStateException("Notification extension bridge construction rollback was unsafe", cause)

/**
 * Narrow spike bridge from the notification framework into an existing authenticated Kalium
 * session. It reuses the normal auth, client-registration, conversation metadata and CoreCrypto
 * providers, but deliberately excludes full incremental sync and all sending/recovery side effects.
 */
public class NotificationExtensionLogicBridge internal constructor(
    private val selfUserId: UserId,
    private val currentClientId: suspend () -> Either<CoreFailure, com.wire.kalium.logic.data.conversation.ClientId>,
    private val notificationApi: NotificationApi,
    private val cryptoTransactionProvider: CryptoTransactionProvider,
    private val conversationMlsGroupId: suspend (QualifiedID) -> String?,
    private val conversationCallType: suspend (QualifiedID) -> Int,
    private val avsIdentifier: suspend (QualifiedID) -> String,
    private val protobufDecoder: ProtobufMessageContentDecoder = ProtobufMessageContentDecoderImpl(selfUserId),
    private val contentExtractor: NotificationContentExtractor = NotificationContentExtractorImpl(),
    private val proteusDecryptor: ProteusMessageDecryptor = ProteusMessageDecryptorImpl(),
    private val mlsDecryptor: MlsMessageDecryptor = MlsMessageDecryptorImpl(),
    private val contentResolver: MessageContentResolver = MessageContentResolverImpl(),
    private val closeResources: () -> Unit = {}
) {
    private val receiveDecoder: MessageContentDecoder<DecodedProtobufContent> =
        NotificationExtensionProtobufDecoderAdapter(protobufDecoder)
    private val resourcesClosed = AtomicInt(RESOURCES_OPEN)
    private val transportShutdownState = AtomicInt(TRANSPORT_SHUTDOWN_SAFE)

    /** Returns the locally registered client for this account, without registering a new one. */
    public suspend fun resolveClientId(): String? = when (val result = currentClientId()) {
        is Either.Left -> null
        is Either.Right -> result.value.value
    }

    /** Resolves the same federation-aware self identifier used by the application AVS path. */
    public suspend fun resolveSelfAvsUserId(): String = avsIdentifier(selfUserId)

    /** Builds the exact notification-only AVS input for one decrypted calling message. */
    @Suppress("ReturnCount")
    public suspend fun resolveCallEvent(
        message: NotificationExtensionLogicMessage
    ): NotificationExtensionLogicCallEvent? {
        val candidate = message.candidate
        if (candidate == null || candidate.kind != NotificationExtensionLogicContentKind.CALLING) return null
        val payload = candidate.callPayload
        val senderClientId = message.senderClientId
        if (payload == null || senderClientId == null) return null
        val envelopeConversationId = QualifiedID(message.conversationId, message.conversationDomain)
        val embeddedConversationId = candidate.callConversationId?.let { value ->
            QualifiedID(value, candidate.callConversationDomain.orEmpty())
        }
        val senderIsSelf = message.senderId == selfUserId.value && message.senderDomain == selfUserId.domain
        val targetConversationId = if (senderIsSelf) {
            embeddedConversationId ?: envelopeConversationId
        } else {
            envelopeConversationId
        }
        return NotificationExtensionLogicCallEvent(
            payload = payload,
            currentTimeSeconds = Clock.System.now().epochSeconds,
            messageTimeSeconds = message.timestampEpochMillis / MILLIS_PER_SECOND,
            conversationId = avsIdentifier(targetConversationId),
            senderUserId = avsIdentifier(QualifiedID(message.senderId, message.senderDomain)),
            senderClientId = senderClientId,
            conversationType = conversationCallType(targetConversationId)
        )
    }

    /** Cancels resources owned by this passive account bridge. This operation is idempotent. */
    public fun close() {
        if (!resourcesClosed.compareAndSet(RESOURCES_OPEN, RESOURCES_CLOSED)) return
        val resourceFailure = runCatching(closeResources).exceptionOrNull()
        if (transportShutdownState.load() == TRANSPORT_SHUTDOWN_UNSAFE) {
            val shutdownFailure = IllegalStateException("Notification transport collector did not stop within its teardown bound")
            resourceFailure?.let(shutdownFailure::addSuppressed)
            throw shutdownFailure
        }
        resourceFailure?.let { throw it }
    }

    /** Opens one authenticated, marker-bounded consumable-notification session. */
    public suspend fun openTransport(
        clientId: String,
        markerId: String
    ): NotificationExtensionLogicTransportOpenResult {
        val flow = when (val response = notificationApi.consumeLiveEvents(clientId, markerId)) {
            is NetworkResponse.Error -> return NotificationExtensionLogicTransportOpenResult(
                status = NotificationExtensionLogicTransportOpenStatus.RETRYABLE_FAILURE,
                session = null
            )

            is NetworkResponse.Success -> response.value
        }
        val session = NotificationExtensionLogicTransportSession(
            clientId = clientId,
            markerId = markerId,
            notificationApi = notificationApi,
            events = flow,
            transportShutdownState = transportShutdownState
        )
        return when (session.initialize()) {
            NotificationExtensionLogicTransportOpenStatus.OPENED -> NotificationExtensionLogicTransportOpenResult(
                status = NotificationExtensionLogicTransportOpenStatus.OPENED,
                session = session
            )

            NotificationExtensionLogicTransportOpenStatus.RETRYABLE_FAILURE -> {
                session.close()
                NotificationExtensionLogicTransportOpenResult(
                    status = NotificationExtensionLogicTransportOpenStatus.RETRYABLE_FAILURE,
                    session = null
                )
            }

            NotificationExtensionLogicTransportOpenStatus.TERMINAL_FAILURE -> {
                session.close()
                NotificationExtensionLogicTransportOpenResult(
                    status = NotificationExtensionLogicTransportOpenStatus.TERMINAL_FAILURE,
                    session = null
                )
            }
        }
    }

    /**
     * Applies one captured event and extracts exact GenericMessage protobufs.
     *
     * [materializer] is invoked with the complete child batch before the enclosing CoreCrypto
     * transaction may commit. Any non-durable result aborts that transaction. This ordering makes
     * a committed Proteus/MLS state transition imply that a foreground-importable child batch
     * already exists.
     */
    public suspend fun receive(
        rawEnvelope: ByteArray,
        materializer: NotificationExtensionLogicMaterializer
    ): NotificationExtensionLogicReceiveResult = try {
        receiveAndMaterialize(rawEnvelope, materializer)
    } finally {
        // The engine invokes receive while owning the process lease. Close before returning so no
        // NSE-owned CoreCrypto handle can survive the lease release and race the foreground app.
        withContext(NonCancellable) {
            cryptoTransactionProvider.closeClients()
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receiveAndMaterialize(
        rawEnvelope: ByteArray,
        materializer: NotificationExtensionLogicMaterializer
    ): NotificationExtensionLogicReceiveResult {
        val storedEvent = try {
            decodeNotificationExtensionStoredEvent(rawEnvelope)
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Throwable) {
            return NotificationExtensionLogicReceiveResult(
                status = NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE,
                messages = emptyList()
            )
        }
        val payload = try {
            storedEvent.toEventResponse().payload.orEmpty()
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Throwable) {
            return NotificationExtensionLogicReceiveResult(
                status = NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE,
                messages = emptyList()
            )
        }

        val hasCryptoPayload = payload.any {
            it is EventContentDTO.Conversation.NewMessageDTO ||
                    it is EventContentDTO.Conversation.NewMLSMessageDTO
        }
        if (!hasCryptoPayload) {
            return receivePayload(storedEvent.id, payload, null)
        }
        return try {
            when (
                val transaction = cryptoTransactionProvider.transaction("notification-extension-receive") { context ->
                    val result = receivePayload(storedEvent.id, payload, context)
                    if (result.messages.isNotEmpty()) {
                        when (materializer.materialize(result.messages, result.status)) {
                            NotificationExtensionLogicMaterializationStatus.DURABLE -> Unit
                            NotificationExtensionLogicMaterializationStatus.RETRYABLE_FAILURE ->
                                throw NotificationExtensionMaterializationAbort(retryable = true)

                            NotificationExtensionLogicMaterializationStatus.TERMINAL_FAILURE ->
                                throw NotificationExtensionMaterializationAbort(retryable = false)
                        }
                    }
                    Either.Right(result)
                }
            ) {
                is Either.Left -> NotificationExtensionLogicReceiveResult(
                    status = NotificationExtensionLogicReceiveStatus.FOREGROUND_REQUIRED,
                    messages = emptyList()
                )

                is Either.Right -> transaction.value
            }
        } catch (abort: NotificationExtensionMaterializationAbort) {
            NotificationExtensionLogicReceiveResult(
                status = if (abort.retryable) {
                    NotificationExtensionLogicReceiveStatus.RETRYABLE_FAILURE
                } else {
                    NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE
                },
                messages = emptyList()
            )
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receivePayload(
        eventId: String,
        payload: List<EventContentDTO>,
        context: CryptoTransactionContext?
    ): NotificationExtensionLogicReceiveResult {
        val messages = mutableListOf<NotificationExtensionLogicMessage>()
        var nextItemIndex = 0
        var requiresForeground = false
        for (item in payload) {
            val result = when (item) {
                is EventContentDTO.Conversation.NewMessageDTO -> context?.proteus?.let {
                    receiveProteus(it, eventId, item, nextItemIndex)
                } ?: ReceiveItemResult.ForegroundRequired(emptyList())

                is EventContentDTO.Conversation.NewMLSMessageDTO ->
                    receiveMls(context?.mls, eventId, item, nextItemIndex)

                is EventContentDTO.Conversation.MLSWelcomeDTO -> ReceiveItemResult.ForegroundRequired(emptyList())
                else -> ReceiveItemResult.Applied(emptyList())
            }
            when (result) {
                is ReceiveItemResult.Applied -> {
                    messages += result.messages
                    nextItemIndex = nextReceiveChildIndex(nextItemIndex, result.messages.size)
                }

                is ReceiveItemResult.ForegroundRequired -> {
                    messages += result.messages
                    nextItemIndex = nextReceiveChildIndex(nextItemIndex, result.messages.size)
                    requiresForeground = true
                }

                ReceiveItemResult.RetryableFailure -> return NotificationExtensionLogicReceiveResult(
                    status = NotificationExtensionLogicReceiveStatus.RETRYABLE_FAILURE,
                    messages = messages
                )

                ReceiveItemResult.TerminalFailure -> return NotificationExtensionLogicReceiveResult(
                    status = NotificationExtensionLogicReceiveStatus.TERMINAL_FAILURE,
                    messages = messages
                )
            }
        }
        return NotificationExtensionLogicReceiveResult(
            status = if (requiresForeground) {
                NotificationExtensionLogicReceiveStatus.FOREGROUND_REQUIRED
            } else {
                NotificationExtensionLogicReceiveStatus.MATERIALIZED
            },
            messages = messages
        )
    }

    @Suppress("ReturnCount")
    private suspend fun receiveProteus(
        context: ProteusCoreCryptoContext,
        eventId: String,
        event: EventContentDTO.Conversation.NewMessageDTO,
        itemIndex: Int
    ): ReceiveItemResult {
        val encryptedMessage = runCatching { Base64.decode(event.data.text) }
            .getOrElse { return ReceiveItemResult.TerminalFailure }
        val encryptedExternalContent = event.data.encryptedExternalData?.let {
            runCatching { Base64.decode(it) }.getOrElse { return ReceiveItemResult.TerminalFailure }
        }
        val result = wrapProteusRequest {
            proteusDecryptor.decrypt(
                context = context,
                message = ProteusEncryptedMessage(
                    sessionId = CryptoSessionId(
                        userId = CryptoQualifiedID(event.qualifiedFrom.value, event.qualifiedFrom.domain),
                        cryptoClientId = CryptoClientId(event.data.sender)
                    ),
                    encryptedMessage = encryptedMessage
                )
            ) { decryptedMessage ->
                when (
                    val resolution = contentResolver.resolveProteusContent(
                        decryptedMessage = decryptedMessage,
                        encryptedExternalContent = encryptedExternalContent,
                        decoder = receiveDecoder
                    )
                ) {
                    is MessageContentResolution.InvalidExternalContent ->
                        Either.Left(CoreFailure.Unknown(resolution.cause))

                    is MessageContentResolution.Success -> Either.Right(
                        resolution.message.content.toLogicMessage(
                            eventId = eventId,
                            itemIndex = itemIndex,
                            conversationId = event.qualifiedConversation.toLogicId(),
                            senderId = event.qualifiedFrom.toLogicId(),
                            senderClientId = event.data.sender,
                            timestampEpochMillis = event.time.toEpochMilliseconds(),
                            protocol = NotificationExtensionLogicProtocol.PROTEUS
                        )
                    )
                }
            }
        }.flatMap { it }
        return when (result) {
            is Either.Left -> ReceiveItemResult.ForegroundRequired(emptyList())
            is Either.Right -> ReceiveItemResult.Applied(listOf(result.value))
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receiveMls(
        context: MlsCoreCryptoContext?,
        eventId: String,
        event: EventContentDTO.Conversation.NewMLSMessageDTO,
        itemIndex: Int
    ): ReceiveItemResult {
        val mlsContext = context ?: return ReceiveItemResult.ForegroundRequired(emptyList())
        val conversationId = event.qualifiedConversation.toLogicId()
        // Subconversation membership is process-local in the application graph. The NSE must not
        // fetch or join it, so those messages are deliberately handed back to the foreground app.
        if (event.subconversation != null) return ReceiveItemResult.ForegroundRequired(emptyList())
        val groupId = try {
            conversationMlsGroupId(conversationId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return ReceiveItemResult.ForegroundRequired(emptyList())
        val encryptedMessage = runCatching { Base64.decode(event.message) }
            .getOrElse { return ReceiveItemResult.TerminalFailure }
        val result = wrapMLSRequest {
            mlsDecryptor.decrypt(
                context = mlsContext,
                message = MlsEncryptedMessage(groupId, encryptedMessage)
            ) { decryptedMessages ->
                val output = mutableListOf<NotificationExtensionLogicMessage>()
                var foregroundRequired = false
                decryptedMessages.forEach { decrypted ->
                    if (decrypted.commitDelay != null || !decrypted.crlNewDistributionPoints.isNullOrEmpty()) {
                        foregroundRequired = true
                    }
                    decrypted.decryptedMessage?.let { serializedContent ->
                        val decoded = protobufDecoder.decode(serializedContent)
                        val sender = decrypted.senderClientId
                        output += decoded.toLogicMessage(
                            eventId = eventId,
                            itemIndex = nextEmittedMlsChildIndex(itemIndex, output.size),
                            conversationId = conversationId,
                            senderId = sender?.userId?.let { QualifiedID(it.value, it.domain) }
                                ?: event.qualifiedFrom.toLogicId(),
                            senderClientId = sender?.value,
                            timestampEpochMillis = event.time.toEpochMilliseconds(),
                            protocol = NotificationExtensionLogicProtocol.MLS
                        )
                    }
                }
                if (foregroundRequired) {
                    ReceiveItemResult.ForegroundRequired(output)
                } else {
                    ReceiveItemResult.Applied(output)
                }
            }
        }
        return when (result) {
            is Either.Left -> ReceiveItemResult.ForegroundRequired(emptyList())
            is Either.Right -> result.value
        }
    }

    private fun DecodedProtobufContent.toLogicMessage(
        eventId: String,
        itemIndex: Int,
        conversationId: QualifiedID,
        senderId: QualifiedID,
        senderClientId: String?,
        timestampEpochMillis: Long,
        protocol: NotificationExtensionLogicProtocol
    ): NotificationExtensionLogicMessage = NotificationExtensionLogicMessage(
        eventId = eventId,
        itemIndex = itemIndex,
        conversationId = conversationId.value,
        conversationDomain = conversationId.domain,
        senderId = senderId.value,
        senderDomain = senderId.domain,
        senderClientId = senderClientId,
        timestampEpochMillis = timestampEpochMillis,
        protocol = protocol,
        serializedContent = serializedContent,
        candidate = when (val extracted = contentExtractor.extract(this)) {
            is NotificationContentExtractionResult.Candidate -> extracted.toLogicCandidate()
            is NotificationContentExtractionResult.ExternalRequiresResolution,
            is NotificationContentExtractionResult.KnownNotNotifiable,
            is NotificationContentExtractionResult.Unsupported -> null
        }
    )
}

public enum class NotificationExtensionLogicTransportOpenStatus {
    OPENED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

public class NotificationExtensionLogicTransportOpenResult internal constructor(
    public val status: NotificationExtensionLogicTransportOpenStatus,
    public val session: NotificationExtensionLogicTransportSession?
)

public enum class NotificationExtensionLogicTransportMode {
    CONSUMABLE,
    LEGACY
}

public enum class NotificationExtensionLogicTransportReceiveStatus {
    RECEIVED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

public class NotificationExtensionLogicTransportReceiveResult internal constructor(
    public val status: NotificationExtensionLogicTransportReceiveStatus,
    public val frame: NotificationExtensionLogicTransportFrame?
)

public enum class NotificationExtensionLogicTransportAckStatus {
    ACCEPTED_BY_LOCAL_WRITER,
    REJECTED_RETRYABLE,
    REJECTED_TERMINAL
}

public sealed interface NotificationExtensionLogicTransportFrame {
    public class Event internal constructor(
        public val eventId: String,
        rawEnvelope: ByteArray,
        public val isTransient: Boolean,
        public val cursor: String?,
        public val deliveryTag: ULong?
    ) : NotificationExtensionLogicTransportFrame {
        private val ownedRawEnvelope: ByteArray = rawEnvelope.copyOf()
        public val rawEnvelope: ByteArray get() = ownedRawEnvelope.copyOf()
    }

    public class SynchronizationMarker internal constructor(
        public val markerId: String,
        public val deliveryTag: ULong?
    ) : NotificationExtensionLogicTransportFrame

    public data object MissedNotification : NotificationExtensionLogicTransportFrame
    public data object Closed : NotificationExtensionLogicTransportFrame
    public data object UnexpectedPayload : NotificationExtensionLogicTransportFrame
}

public class NotificationExtensionLogicTransportSession internal constructor(
    private val clientId: String,
    private val markerId: String,
    private val notificationApi: NotificationApi,
    events: Flow<WebSocketEvent<ConsumableNotificationResponse>>,
    private val transportShutdownState: AtomicInt
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val channel = Channel<WebSocketEvent<ConsumableNotificationResponse>>(Channel.RENDEZVOUS)
    private val collector = scope.launch {
        events
            .onCompletion { cause -> channel.close(cause) }
            .collect { channel.send(it) }
    }
    private var resolvedMode: NotificationExtensionLogicTransportMode? = null

    public val mode: NotificationExtensionLogicTransportMode
        get() = checkNotNull(resolvedMode) { "Transport session was not initialized" }

    @Suppress("ReturnCount")
    internal suspend fun initialize(): NotificationExtensionLogicTransportOpenStatus {
        val first = channel.receiveCatching()
        if (first.isClosed) {
            return if (first.exceptionOrNull() == null) {
                NotificationExtensionLogicTransportOpenStatus.TERMINAL_FAILURE
            } else {
                NotificationExtensionLogicTransportOpenStatus.RETRYABLE_FAILURE
            }
        }
        val open = first.getOrNull() as? WebSocketEvent.Open
            ?: return NotificationExtensionLogicTransportOpenStatus.TERMINAL_FAILURE
        resolvedMode = if (open.shouldProcessPendingEvents) {
            NotificationExtensionLogicTransportMode.LEGACY
        } else {
            NotificationExtensionLogicTransportMode.CONSUMABLE
        }
        return NotificationExtensionLogicTransportOpenStatus.OPENED
    }

    public suspend fun receive(): NotificationExtensionLogicTransportReceiveResult {
        val received = channel.receiveCatching()
        if (received.isClosed) {
            return NotificationExtensionLogicTransportReceiveResult(
                status = if (received.exceptionOrNull() == null) {
                    NotificationExtensionLogicTransportReceiveStatus.RECEIVED
                } else {
                    NotificationExtensionLogicTransportReceiveStatus.RETRYABLE_FAILURE
                },
                frame = if (received.exceptionOrNull() == null) {
                    NotificationExtensionLogicTransportFrame.Closed
                } else {
                    null
                }
            )
        }
        val frame = when (val event = received.getOrThrow()) {
            is WebSocketEvent.BinaryPayloadReceived -> event.toLogicFrame()
            is WebSocketEvent.Close -> NotificationExtensionLogicTransportFrame.Closed
            is WebSocketEvent.NonBinaryPayloadReceived,
            is WebSocketEvent.Open -> NotificationExtensionLogicTransportFrame.UnexpectedPayload
        }
        return NotificationExtensionLogicTransportReceiveResult(
            status = NotificationExtensionLogicTransportReceiveStatus.RECEIVED,
            frame = frame
        )
    }

    public suspend fun acknowledge(deliveryTag: ULong): NotificationExtensionLogicTransportAckStatus = try {
        notificationApi.acknowledgeEvents(
            clientId = clientId,
            markerId = markerId,
            eventAcknowledgeRequest = EventAcknowledgeRequest(
                type = AcknowledgeType.ACK,
                data = AcknowledgeData(deliveryTag)
            )
        ).toLogicTransportAckStatus()
    } catch (_: CancellationException) {
        throw CancellationException()
    } catch (_: Throwable) {
        NotificationExtensionLogicTransportAckStatus.REJECTED_RETRYABLE
    }

    public fun close() {
        val stoppedWithinBound = runBlocking {
            withTimeoutOrNull(TRANSPORT_COLLECTOR_CLOSE_TIMEOUT_MILLIS) {
                collector.cancelAndJoin()
                true
            } ?: false
        }
        if (!stoppedWithinBound) {
            transportShutdownState.store(TRANSPORT_SHUTDOWN_UNSAFE)
        }
        channel.close()
        scope.cancel()
    }
}

internal fun EventAcknowledgeResult.toLogicTransportAckStatus(): NotificationExtensionLogicTransportAckStatus =
    when (this) {
        EventAcknowledgeResult.ACCEPTED_BY_LOCAL_WRITER ->
            NotificationExtensionLogicTransportAckStatus.ACCEPTED_BY_LOCAL_WRITER

        EventAcknowledgeResult.RETRYABLE_FAILURE ->
            NotificationExtensionLogicTransportAckStatus.REJECTED_RETRYABLE

        EventAcknowledgeResult.TERMINAL_FAILURE ->
            NotificationExtensionLogicTransportAckStatus.REJECTED_TERMINAL
    }

internal fun nextReceiveChildIndex(currentIndex: Int, emittedMessageCount: Int): Int {
    require(currentIndex >= 0 && emittedMessageCount >= 0)
    return currentIndex + emittedMessageCount
}

internal fun nextEmittedMlsChildIndex(itemIndex: Int, emittedMessageCount: Int): Int =
    nextReceiveChildIndex(itemIndex, emittedMessageCount)

public enum class NotificationExtensionLogicReceiveStatus {
    MATERIALIZED,
    FOREGROUND_REQUIRED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

public enum class NotificationExtensionLogicMaterializationStatus {
    DURABLE,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
}

/** Durable callback invoked inside the event's CoreCrypto transaction. */
public fun interface NotificationExtensionLogicMaterializer {
    public suspend fun materialize(
        messages: List<NotificationExtensionLogicMessage>,
        receiveStatus: NotificationExtensionLogicReceiveStatus
    ): NotificationExtensionLogicMaterializationStatus
}

public class NotificationExtensionLogicReceiveResult internal constructor(
    public val status: NotificationExtensionLogicReceiveStatus,
    messages: List<NotificationExtensionLogicMessage>
) {
    public val messages: List<NotificationExtensionLogicMessage> = messages.toList()
}

@Suppress("LongParameterList")
public class NotificationExtensionLogicCallEvent internal constructor(
    public val payload: String,
    public val currentTimeSeconds: Long,
    public val messageTimeSeconds: Long,
    public val conversationId: String,
    public val senderUserId: String,
    public val senderClientId: String,
    public val conversationType: Int
) {
    override fun toString(): String = "NotificationExtensionLogicCallEvent(redacted)"
}

public enum class NotificationExtensionLogicProtocol {
    PROTEUS,
    MLS
}

@Suppress("LongParameterList")
public class NotificationExtensionLogicMessage internal constructor(
    public val eventId: String,
    public val itemIndex: Int,
    public val conversationId: String,
    public val conversationDomain: String,
    public val senderId: String,
    public val senderDomain: String,
    public val senderClientId: String?,
    public val timestampEpochMillis: Long,
    public val protocol: NotificationExtensionLogicProtocol,
    serializedContent: ByteArray,
    public val candidate: NotificationExtensionLogicCandidate?
) {
    private val ownedSerializedContent: ByteArray = serializedContent.copyOf()
    public val serializedContent: ByteArray get() = ownedSerializedContent.copyOf()
}

public enum class NotificationExtensionLogicContentKind {
    TEXT,
    ASSET,
    MULTIPART,
    EDIT,
    DELETE,
    REACTION,
    CALLING,
    KNOCK,
    LOCATION
}

public class NotificationExtensionLogicCandidate internal constructor(
    public val messageId: String,
    public val kind: NotificationExtensionLogicContentKind,
    public val body: String?,
    public val mentionsSelf: Boolean,
    public val legalHoldStatus: String,
    public val expiresAfterMillis: Long?,
    public val callPayload: String?,
    public val callConversationId: String?,
    public val callConversationDomain: String?
)

private sealed interface ReceiveItemResult {
    data class Applied(val messages: List<NotificationExtensionLogicMessage>) : ReceiveItemResult
    data class ForegroundRequired(val messages: List<NotificationExtensionLogicMessage>) : ReceiveItemResult
    data object RetryableFailure : ReceiveItemResult
    data object TerminalFailure : ReceiveItemResult
}

private class NotificationExtensionMaterializationAbort(
    val retryable: Boolean
) : Exception()

private class NotificationExtensionProtobufDecoderAdapter(
    private val decoder: ProtobufMessageContentDecoder
) : MessageContentDecoder<DecodedProtobufContent> {
    override fun decode(serializedContent: ByteArray): DecodedMessageContent<DecodedProtobufContent> {
        val decoded = decoder.decode(serializedContent)
        return if (decoded.classification == DecodedProtobufContent.Classification.EXTERNAL_INSTRUCTIONS) {
            val instructions = decoded.content as? ProtoContent.ExternalMessageInstructions
                ?: return DecodedMessageContent.Application(decoded)
            DecodedMessageContent.ExternalInstructions(instructions.otrKey)
        } else {
            DecodedMessageContent.Application(decoded)
        }
    }
}

internal fun WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>.toLogicFrame():
        NotificationExtensionLogicTransportFrame = when (val decodedPayload = payload) {
    is ConsumableNotificationResponse.EventNotification -> {
        val exactEventEnvelope = extractExactConsumableEventEnvelope(
            requireNotNull(rawPayload) {
                "Exact WebSocket bytes are required for notification-extension durable capture"
            }
        )
        check(decodeNotificationExtensionStoredEvent(exactEventEnvelope) == decodedPayload.data.event) {
            "Decoded consumable metadata does not match the exact event envelope"
        }
        NotificationExtensionLogicTransportFrame.Event(
            eventId = decodedPayload.data.event.id,
            rawEnvelope = exactEventEnvelope,
            isTransient = decodedPayload.data.event.transient,
            cursor = if (decodedPayload.data.event.transient) null else decodedPayload.data.event.id,
            deliveryTag = decodedPayload.data.deliveryTag
        )
    }

    is ConsumableNotificationResponse.SynchronizationNotification ->
        NotificationExtensionLogicTransportFrame.SynchronizationMarker(
            decodedPayload.data.markerId,
            decodedPayload.data.deliveryTag
        )

    ConsumableNotificationResponse.MissedNotification -> NotificationExtensionLogicTransportFrame.MissedNotification
}

internal fun decodeNotificationExtensionStoredEvent(rawEnvelope: ByteArray): EventResponseToStore {
    return KtxSerializer.json.decodeFromString<EventResponseToStore>(rawEnvelope.decodeToString())
}

/**
 * Extracts the exact `data.event` JSON value from a consumable frame.
 *
 * Delivery tags are transport-only and must never cross the durable inbox boundary. A small
 * structural scanner is used instead of DTO re-encoding so unknown event fields, ordering and
 * whitespace remain byte-exact while the existing event-only envelope format stays version 1.
 */
internal fun extractExactConsumableEventEnvelope(rawFrame: ByteArray): ByteArray {
    val root = rawFrame.findJsonObjectMember("data", 0, rawFrame.size)
        ?: error("Consumable notification frame has no data object")
    val event = rawFrame.findJsonObjectMember("event", root.first, root.last + 1)
        ?: error("Consumable notification frame has no event object")
    return rawFrame.copyOfRange(event.first, event.last + 1)
}

private fun ByteArray.findJsonObjectMember(
    memberName: String,
    rangeStart: Int,
    rangeEndExclusive: Int
): IntRange? {
    var index = skipJsonWhitespace(rangeStart, rangeEndExclusive)
    if (index >= rangeEndExclusive || this[index] != JSON_OBJECT_START) return null
    var matchingRange: IntRange? = null
    index += 1
    while (index < rangeEndExclusive) {
        index = skipJsonWhitespace(index, rangeEndExclusive)
        if (index >= rangeEndExclusive) return null
        if (this[index] == JSON_OBJECT_END) return matchingRange
        if (this[index] != JSON_QUOTE) return null
        val keyEnd = jsonStringEnd(index, rangeEndExclusive) ?: return null
        val keyMatches = jsonStringEquals(index, keyEnd, memberName)
        index = skipJsonWhitespace(keyEnd, rangeEndExclusive)
        if (index >= rangeEndExclusive || this[index] != JSON_NAME_SEPARATOR) return null
        val valueStart = skipJsonWhitespace(index + 1, rangeEndExclusive)
        val valueEnd = jsonValueEnd(valueStart, rangeEndExclusive) ?: return null
        if (keyMatches) {
            if (matchingRange != null) return null
            matchingRange = valueStart until valueEnd
        }
        index = skipJsonWhitespace(valueEnd, rangeEndExclusive)
        if (index < rangeEndExclusive && this[index] == JSON_VALUE_SEPARATOR) {
            index += 1
        } else if (index >= rangeEndExclusive || this[index] != JSON_OBJECT_END) {
            return null
        }
    }
    return null
}

private fun ByteArray.jsonValueEnd(start: Int, endExclusive: Int): Int? {
    if (start >= endExclusive) return null
    return when (this[start]) {
        JSON_QUOTE -> jsonStringEnd(start, endExclusive)
        JSON_OBJECT_START, JSON_ARRAY_START -> jsonCompositeEnd(start, endExclusive)
        else -> {
            var index = start
            while (
                index < endExclusive &&
                this[index] != JSON_VALUE_SEPARATOR &&
                this[index] != JSON_OBJECT_END &&
                this[index] != JSON_ARRAY_END &&
                !this[index].isJsonWhitespace()
            ) {
                index += 1
            }
            index.takeIf { it > start }
        }
    }
}

private fun ByteArray.jsonCompositeEnd(start: Int, endExclusive: Int): Int? {
    val stack = mutableListOf(this[start])
    var index = start + 1
    while (index < endExclusive) {
        when (this[index]) {
            JSON_QUOTE -> index = jsonStringEnd(index, endExclusive) ?: return null
            JSON_OBJECT_START, JSON_ARRAY_START -> {
                stack += this[index]
                index += 1
            }
            JSON_OBJECT_END -> {
                if (stack.removeLastOrNull() != JSON_OBJECT_START) return null
                index += 1
                if (stack.isEmpty()) return index
            }
            JSON_ARRAY_END -> {
                if (stack.removeLastOrNull() != JSON_ARRAY_START) return null
                index += 1
                if (stack.isEmpty()) return index
            }
            else -> index += 1
        }
    }
    return null
}

private fun ByteArray.jsonStringEnd(start: Int, endExclusive: Int): Int? {
    var index = start + 1
    while (index < endExclusive) {
        when (this[index]) {
            JSON_ESCAPE -> index += 2
            JSON_QUOTE -> return index + 1
            else -> index += 1
        }
    }
    return null
}

private fun ByteArray.skipJsonWhitespace(start: Int, endExclusive: Int): Int {
    var index = start
    while (index < endExclusive && this[index].isJsonWhitespace()) index += 1
    return index
}

private fun ByteArray.jsonStringEquals(start: Int, endExclusive: Int, expected: String): Boolean =
    runCatching {
        KtxSerializer.json.decodeFromString<String>(copyOfRange(start, endExclusive).decodeToString())
    }.getOrNull() == expected

private fun Byte.isJsonWhitespace(): Boolean =
    this == JSON_SPACE || this == JSON_TAB || this == JSON_LINE_FEED || this == JSON_CARRIAGE_RETURN

private fun com.wire.kalium.network.api.model.QualifiedID.toLogicId(): QualifiedID = QualifiedID(value, domain)

private fun NotificationContentExtractionResult.Candidate.toLogicCandidate(): NotificationExtensionLogicCandidate {
    val details = when (val content = content) {
        is NotificationContent.Text -> CandidateDetails(NotificationExtensionLogicContentKind.TEXT, content.value, content.mentionsSelf)
        is NotificationContent.Asset -> CandidateDetails(NotificationExtensionLogicContentKind.ASSET, content.name, false)
        is NotificationContent.Multipart -> CandidateDetails(
            NotificationExtensionLogicContentKind.MULTIPART,
            content.text,
            content.mentionsSelf
        )
        is NotificationContent.Edit -> CandidateDetails(
            NotificationExtensionLogicContentKind.EDIT,
            content.replacementText,
            content.mentionsSelf
        )
        is NotificationContent.Delete -> CandidateDetails(NotificationExtensionLogicContentKind.DELETE, null, false)
        is NotificationContent.Reaction -> CandidateDetails(
            NotificationExtensionLogicContentKind.REACTION,
            content.emojiSet.joinToString(separator = ""),
            false
        )
        is NotificationContent.Calling -> CandidateDetails(NotificationExtensionLogicContentKind.CALLING, null, false)
        is NotificationContent.Knock -> CandidateDetails(NotificationExtensionLogicContentKind.KNOCK, null, false)
        is NotificationContent.Location -> CandidateDetails(NotificationExtensionLogicContentKind.LOCATION, content.name, false)
    }
    val callingContent = content as? NotificationContent.Calling
    return NotificationExtensionLogicCandidate(
        messageId = content.messageUid,
        kind = details.kind,
        body = details.body,
        mentionsSelf = details.mentionsSelf,
        legalHoldStatus = legalHoldStatus.name,
        expiresAfterMillis = expiresAfterMillis,
        callPayload = callingContent?.payload,
        callConversationId = callingContent?.conversationId?.value,
        callConversationDomain = callingContent?.conversationId?.domain
    )
}

private data class CandidateDetails(
    val kind: NotificationExtensionLogicContentKind,
    val body: String?,
    val mentionsSelf: Boolean
)

private const val MILLIS_PER_SECOND = 1_000L
private const val RESOURCES_OPEN = 0
private const val RESOURCES_CLOSED = 1
private const val TRANSPORT_SHUTDOWN_SAFE = 0
private const val TRANSPORT_SHUTDOWN_UNSAFE = 1
private const val TRANSPORT_COLLECTOR_CLOSE_TIMEOUT_MILLIS = 250L
private const val JSON_OBJECT_START: Byte = 0x7B
private const val JSON_OBJECT_END: Byte = 0x7D
private const val JSON_ARRAY_START: Byte = 0x5B
private const val JSON_ARRAY_END: Byte = 0x5D
private const val JSON_QUOTE: Byte = 0x22
private const val JSON_ESCAPE: Byte = 0x5C
private const val JSON_NAME_SEPARATOR: Byte = 0x3A
private const val JSON_VALUE_SEPARATOR: Byte = 0x2C
private const val JSON_SPACE: Byte = 0x20
private const val JSON_TAB: Byte = 0x09
private const val JSON_LINE_FEED: Byte = 0x0A
private const val JSON_CARRIAGE_RETURN: Byte = 0x0D
