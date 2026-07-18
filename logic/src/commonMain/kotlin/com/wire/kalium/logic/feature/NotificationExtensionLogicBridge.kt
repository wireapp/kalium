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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.io.encoding.Base64

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

    /** Returns the locally registered client for this account, without registering a new one. */
    public suspend fun resolveClientId(): String? = when (val result = currentClientId()) {
        is Either.Left -> null
        is Either.Right -> result.value.value
    }

    /** Resolves the same federation-aware self identifier used by the application AVS path. */
    public suspend fun resolveSelfAvsUserId(): String = avsIdentifier(selfUserId)

    /** Builds the exact notification-only AVS input for one decrypted calling message. */
    public suspend fun resolveCallEvent(
        message: NotificationExtensionLogicMessage
    ): NotificationExtensionLogicCallEvent? {
        val candidate = message.candidate ?: return null
        if (candidate.kind != NotificationExtensionLogicContentKind.CALLING) return null
        val payload = candidate.callPayload ?: return null
        val senderClientId = message.senderClientId ?: return null
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
        closeResources()
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
            events = flow
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
     * Applies the message payloads in one captured event to CoreCrypto and extracts their exact
     * GenericMessage protobufs. Other event kinds are ignored because this is a receive-only NSE
     * path and the transport remains unacknowledged by the spike assembly.
     */
    public suspend fun receive(rawEnvelope: ByteArray): NotificationExtensionLogicReceiveResult = try {
        receiveAndMaterialize(rawEnvelope)
    } finally {
        // The engine invokes receive while owning the process lease. Close before returning so no
        // NSE-owned CoreCrypto handle can survive the lease release and race the foreground app.
        withContext(NonCancellable) {
            cryptoTransactionProvider.closeClients()
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receiveAndMaterialize(rawEnvelope: ByteArray): NotificationExtensionLogicReceiveResult {
        val storedEvent = try {
            KtxSerializer.json.decodeFromString<EventResponseToStore>(rawEnvelope.decodeToString())
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

        val messages = mutableListOf<NotificationExtensionLogicMessage>()
        var nextItemIndex = 0
        var requiresForeground = false
        for (item in payload) {
            val result = when (item) {
                is EventContentDTO.Conversation.NewMessageDTO -> receiveProteus(storedEvent.id, item, nextItemIndex)
                is EventContentDTO.Conversation.NewMLSMessageDTO -> receiveMls(storedEvent.id, item, nextItemIndex)
                is EventContentDTO.Conversation.MLSWelcomeDTO -> ReceiveItemResult.ForegroundRequired(emptyList())
                else -> ReceiveItemResult.Applied(emptyList())
            }
            when (result) {
                is ReceiveItemResult.Applied -> {
                    messages += result.messages
                    nextItemIndex += result.messages.size.coerceAtLeast(1)
                }

                is ReceiveItemResult.ForegroundRequired -> {
                    messages += result.messages
                    nextItemIndex += result.messages.size.coerceAtLeast(1)
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
        eventId: String,
        event: EventContentDTO.Conversation.NewMessageDTO,
        itemIndex: Int
    ): ReceiveItemResult {
        val encryptedMessage = runCatching { Base64.decode(event.data.text) }
            .getOrElse { return ReceiveItemResult.TerminalFailure }
        val encryptedExternalContent = event.data.encryptedExternalData?.let {
            runCatching { Base64.decode(it) }.getOrElse { return ReceiveItemResult.TerminalFailure }
        }
        val result = cryptoTransactionProvider.proteusTransaction("notification-extension-receive") { context ->
            wrapProteusRequest {
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
        }
        return when (result) {
            is Either.Left -> ReceiveItemResult.ForegroundRequired(emptyList())
            is Either.Right -> ReceiveItemResult.Applied(listOf(result.value))
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun receiveMls(
        eventId: String,
        event: EventContentDTO.Conversation.NewMLSMessageDTO,
        itemIndex: Int
    ): ReceiveItemResult {
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
        val result = cryptoTransactionProvider.mlsTransaction("notification-extension-receive") { context ->
            wrapMLSRequest {
                mlsDecryptor.decrypt(
                    context = context,
                    message = MlsEncryptedMessage(groupId, encryptedMessage)
                ) { decryptedMessages ->
                    val output = mutableListOf<NotificationExtensionLogicMessage>()
                    var foregroundRequired = false
                    decryptedMessages.forEachIndexed { bundleIndex, decrypted ->
                        if (decrypted.commitDelay != null || !decrypted.crlNewDistributionPoints.isNullOrEmpty()) {
                            foregroundRequired = true
                        }
                        decrypted.decryptedMessage?.let { serializedContent ->
                            val decoded = protobufDecoder.decode(serializedContent)
                            val sender = decrypted.senderClientId
                            output += decoded.toLogicMessage(
                                eventId = eventId,
                                itemIndex = itemIndex + bundleIndex,
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
    events: Flow<WebSocketEvent<ConsumableNotificationResponse>>
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
            is WebSocketEvent.BinaryPayloadReceived -> event.payload.toLogicFrame()
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
        )
        NotificationExtensionLogicTransportAckStatus.ACCEPTED_BY_LOCAL_WRITER
    } catch (_: CancellationException) {
        throw CancellationException()
    } catch (_: Throwable) {
        NotificationExtensionLogicTransportAckStatus.REJECTED_RETRYABLE
    }

    public fun close() {
        collector.cancel()
        channel.close()
        scope.cancel()
    }
}

public enum class NotificationExtensionLogicReceiveStatus {
    MATERIALIZED,
    FOREGROUND_REQUIRED,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE
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

private fun ConsumableNotificationResponse.toLogicFrame(): NotificationExtensionLogicTransportFrame = when (this) {
    is ConsumableNotificationResponse.EventNotification -> NotificationExtensionLogicTransportFrame.Event(
        eventId = data.event.id,
        rawEnvelope = KtxSerializer.json.encodeToString(data.event).encodeToByteArray(),
        isTransient = data.event.transient,
        cursor = if (data.event.transient) null else data.event.id,
        deliveryTag = data.deliveryTag
    )

    is ConsumableNotificationResponse.SynchronizationNotification ->
        NotificationExtensionLogicTransportFrame.SynchronizationMarker(data.markerId, data.deliveryTag)

    ConsumableNotificationResponse.MissedNotification -> NotificationExtensionLogicTransportFrame.MissedNotification
}

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
