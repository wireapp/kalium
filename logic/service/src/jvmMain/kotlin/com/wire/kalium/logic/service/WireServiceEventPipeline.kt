@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.conversation.ExperimentalConversationApi::class,
    com.wire.kalium.events.ExperimentalEventApi::class,
    com.wire.kalium.event.processing.ExperimentalEventProcessingApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

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
 */

package com.wire.kalium.logic.service

import com.wire.kalium.calling.runtime.CallingPayloadExtractor
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.calling.runtime.IncomingCallingPayload
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.conversation.ConversationProtocolStateResult
import com.wire.kalium.conversation.ConversationProtocolStateStore
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.cryptography.utils.decryptDataWithAES256
import com.wire.kalium.event.processing.EventDecoder
import com.wire.kalium.event.processing.EventDecryptor
import com.wire.kalium.event.processing.EventHandler
import com.wire.kalium.event.processing.EventHandlerRequirement
import com.wire.kalium.event.processing.EventHandlingContext
import com.wire.kalium.event.processing.EventHandlingResult
import com.wire.kalium.event.processing.EventTransformationResult
import com.wire.kalium.events.EventAcknowledgement
import com.wire.kalium.events.EventCursor
import com.wire.kalium.events.EventDeliveryState
import com.wire.kalium.events.EventDeliveryStateResult
import com.wire.kalium.events.EventDeliveryStateStore
import com.wire.kalium.events.EventIdempotencyKey
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import com.wire.kalium.logic.service.api.ServiceIdentity
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException

/** Parsed aggregate notification retaining the durable delivery key used by the crypto journal. */
@ExperimentalKaliumServiceApi
public data class WireDecodedEvent(public val key: EventIdempotencyKey, public val event: EventResponse)

/** Required protocol changes produced while decrypting one aggregate notification. */
@ExperimentalKaliumServiceApi
public sealed interface WireProtocolOperation {
    public data object ProteusMessageProcessed : WireProtocolOperation

    public data class MlsMessageProcessed(
        public val conversationId: ConversationId,
        public val groupId: GroupID,
        public val epoch: ULong,
    ) : WireProtocolOperation

    public data class MlsWelcomeProcessed(
        public val conversationId: ConversationId,
        public val groupId: GroupID,
    ) : WireProtocolOperation

    public data class ProtocolUpdated(
        public val conversationId: ConversationId,
        public val protocol: ConvProtocol,
    ) : WireProtocolOperation

    public data class MlsReset(
        public val conversationId: ConversationId,
        public val oldGroupId: GroupID,
        public val newGroupId: GroupID,
    ) : WireProtocolOperation

    public data class SelfClientRemoved(public val clientId: String) : WireProtocolOperation

    public data class ConversationAccessRevoked(public val conversationId: ConversationId) : WireProtocolOperation

    public data class CrlDistributionPoints(public val values: List<String>) : WireProtocolOperation

    public data class MlsCommitPendingProposals(
        public val groupId: GroupID,
        public val commitAtEpochSeconds: Long,
    ) : WireProtocolOperation
}

/** Calling-only result plus the protocol effects that must complete before backend ACK. */
@ExperimentalKaliumServiceApi
public data class WireDecryptedEvent(
    public val callingPayloads: List<IncomingCallingPayload>,
    public val protocolOperations: List<WireProtocolOperation>,
)

/** Strict decoder for the backend's aggregate raw JSON payload. */
@ExperimentalKaliumServiceApi
public class WireServiceEventDecoder : EventDecoder<WireRawEvent, WireDecodedEvent> {
    override suspend fun decode(rawEvent: WireRawEvent): EventTransformationResult<WireDecodedEvent> = try {
        EventTransformationResult.Success(WireDecodedEvent(rawEvent.key, rawEvent.event.toEventResponse()))
    } catch (failure: Throwable) {
        EventTransformationResult.Failure("Unable to decode the aggregate Wire notification", failure)
    }
}

/**
 * Mutates durable Proteus/MLS state exactly once, then journals the calling/protocol projection.
 * On redelivery after a crash, the journal is returned without decrypting the same ciphertext again.
 */
@ExperimentalKaliumServiceApi
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
public class WireServiceEventDecryptor(
    private val identity: ServiceIdentity,
    private val crypto: WireServiceCryptoRuntime,
    private val conversationApi: ConversationApi,
    private val contextProvider: ConversationContextProvider,
    private val journal: DecryptedEventJournal,
) : EventDecryptor<WireDecodedEvent, WireDecryptedEvent> {
    override suspend fun decrypt(decodedEvent: WireDecodedEvent): EventTransformationResult<WireDecryptedEvent> = try {
        when (val retained = journal.loadEvent(decodedEvent.key.value)) {
            is EncryptedServiceStateResult.Failure -> return EventTransformationResult.Failure(
                retained.description,
                retained.cause,
            )
            is EncryptedServiceStateResult.Success -> retained.value?.let { retainedEvent ->
                if (WireDecryptedEventCodec.isInProgress(retainedEvent)) {
                    return EventTransformationResult.Failure(
                        "Crypto state for this event may already have advanced; operator recovery is required",
                    )
                }
                return EventTransformationResult.Success(WireDecryptedEventCodec.decode(retainedEvent))
            }
        }

        when (val intent = journal.saveEvent(decodedEvent.key.value, WireDecryptedEventCodec.inProgress())) {
            is EncryptedServiceStateResult.Failure -> return EventTransformationResult.Failure(intent.description, intent.cause)
            is EncryptedServiceStateResult.Success -> Unit
        }

        val calls = mutableListOf<IncomingCallingPayload>()
        val operations = mutableListOf<WireProtocolOperation>()
        decodedEvent.event.payload.orEmpty().forEach { content ->
            when (content) {
                is EventContentDTO.Conversation.NewMessageDTO -> {
                    val generic = decryptProteus(content)
                    generic.calling?.let { calling ->
                        calls += calling.toPayload(
                            transportConversationId = content.qualifiedConversation.toModel(),
                            fallbackUserId = content.qualifiedFrom.toModel(),
                            senderClientId = content.data.sender,
                            timestampSeconds = content.time.epochSeconds,
                            messageId = generic.messageId,
                        )
                    }
                    operations += WireProtocolOperation.ProteusMessageProcessed
                }

                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    val parentProtocol = resolveParentMlsProtocol(content.qualifiedConversation.toModel())
                    val groupId = resolveMlsGroup(content, parentProtocol)
                    val bundles = crypto.withMls("service-decrypt-mls-event") { context ->
                        context.decryptMessage(groupId.value, Base64.decode(content.message))
                    }
                    bundles.forEach { bundle ->
                        bundle.commitDelay?.let { delaySeconds ->
                            operations += WireProtocolOperation.MlsCommitPendingProposals(
                                groupId,
                                Math.addExact(content.time.epochSeconds, delaySeconds),
                            )
                        }
                        bundle.crlNewDistributionPoints.orEmpty().takeIf(List<String>::isNotEmpty)?.let { points ->
                            operations += WireProtocolOperation.CrlDistributionPoints(points)
                        }
                        bundle.message?.let { plaintext ->
                            val generic = GenericMessage.decodeFromByteArray(plaintext)
                            generic.calling?.let { calling ->
                                val sender = checkNotNull(bundle.senderClientId) {
                                    "Decrypted MLS calling content is missing its authenticated sender"
                                }
                                calls += calling.toPayload(
                                    transportConversationId = content.qualifiedConversation.toModel(),
                                    fallbackUserId = UserId(sender.userId.value, sender.userId.domain),
                                    senderClientId = sender.value,
                                    timestampSeconds = content.time.epochSeconds,
                                    messageId = generic.messageId,
                                )
                            }
                        }
                    }
                    val parentGroupId = parentProtocol.groupId()
                    val epoch = crypto.withMls("service-read-mls-epoch") { it.conversationEpoch(parentGroupId.value) }
                    operations += WireProtocolOperation.MlsMessageProcessed(
                        content.qualifiedConversation.toModel(),
                        parentGroupId,
                        epoch,
                    )
                }

                is EventContentDTO.Conversation.MLSWelcomeDTO -> {
                    val bundle = crypto.withMls("service-process-mls-welcome") {
                        it.processWelcomeMessage(Base64.decode(content.message))
                    }
                    bundle.crlNewDistributionPoints.orEmpty().takeIf(List<String>::isNotEmpty)?.let { points ->
                        operations += WireProtocolOperation.CrlDistributionPoints(points)
                    }
                    operations += WireProtocolOperation.MlsWelcomeProcessed(
                        content.qualifiedConversation.toModel(),
                        GroupID(bundle.groupId),
                    )
                }

                is EventContentDTO.Conversation.ProtocolUpdate -> operations += WireProtocolOperation.ProtocolUpdated(
                    content.qualifiedConversation.toModel(),
                    content.data.protocol,
                )

                is EventContentDTO.Conversation.MlsResetConversationDTO -> operations += WireProtocolOperation.MlsReset(
                    content.qualifiedConversation.toModel(),
                    GroupID(content.data.groupId),
                    GroupID(content.data.newGroupId),
                )

                is EventContentDTO.User.ClientRemoveDTO -> operations += WireProtocolOperation.SelfClientRemoved(
                    content.client.clientId,
                )

                is EventContentDTO.Conversation.DeletedConversationDTO ->
                    operations += WireProtocolOperation.ConversationAccessRevoked(
                        content.qualifiedConversation.toModel(),
                    )

                is EventContentDTO.Conversation.MemberLeaveDTO -> if (
                    content.removedUsers.qualifiedUserIds.any {
                        it.value == identity.userId.value && it.domain == identity.userId.domain
                    }
                ) {
                    operations += WireProtocolOperation.ConversationAccessRevoked(content.qualifiedConversation.toModel())
                }

                else -> Unit // Chat/application events are deliberately not persisted by this composition.
            }
        }
        val result = WireDecryptedEvent(calls, operations)
        when (val saved = journal.saveEvent(decodedEvent.key.value, WireDecryptedEventCodec.encode(result))) {
            is EncryptedServiceStateResult.Failure -> EventTransformationResult.Failure(saved.description, saved.cause)
            is EncryptedServiceStateResult.Success -> EventTransformationResult.Success(result)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EventTransformationResult.Failure("Unable to decrypt the aggregate Wire notification", failure)
    }

    private suspend fun decryptProteus(event: EventContentDTO.Conversation.NewMessageDTO): GenericMessage =
        crypto.withProteus("service-decrypt-proteus-event") { context ->
            val sessionId = CryptoSessionId(
                CryptoQualifiedID(event.qualifiedFrom.value, event.qualifiedFrom.domain),
                CryptoClientId(event.data.sender),
            )
            context.decryptMessage(sessionId, Base64.decode(event.data.text)) { plaintext ->
                val generic = GenericMessage.decodeFromByteArray(plaintext)
                val external = generic.external ?: return@decryptMessage generic
                val encryptedExternal = requireNotNull(event.data.encryptedExternalData) {
                    "Proteus external message instructions are missing their encrypted payload"
                }
                val readable = decryptDataWithAES256(
                    EncryptedData(Base64.decode(encryptedExternal)),
                    AES256Key(external.otrKey.array),
                ).data
                GenericMessage.decodeFromByteArray(readable).also {
                    require(it.external == null) { "Nested external Proteus messages are not supported" }
                }
            }
        }

    private suspend fun resolveMlsGroup(
        event: EventContentDTO.Conversation.NewMLSMessageDTO,
        parentProtocol: CallConversationProtocol,
    ): GroupID {
        event.subconversation?.let { subconversation ->
            return when (
                val response = conversationApi.fetchSubconversationDetails(
                    event.qualifiedConversation,
                    subconversation,
                )
            ) {
                is NetworkResponse.Error -> throw response.kException
                is NetworkResponse.Success -> GroupID(response.value.groupId)
            }
        }
        return parentProtocol.groupId()
    }

    private suspend fun resolveParentMlsProtocol(conversationId: ConversationId): CallConversationProtocol =
        when (val result = contextProvider.getForCall(conversationId)) {
            is ConversationContextResult.Failure -> error("Unable to resolve MLS conversation: ${result.failure}")
            is ConversationContextResult.Success -> when (val protocol = result.context.protocol) {
                CallConversationProtocol.Proteus -> error("An MLS message targeted a Proteus conversation")
                is CallConversationProtocol.Mls,
                is CallConversationProtocol.Mixed -> protocol
            }
        }

    private fun CallConversationProtocol.groupId(): GroupID = when (this) {
        CallConversationProtocol.Proteus -> error("Proteus has no MLS group")
        is CallConversationProtocol.Mls -> groupId
        is CallConversationProtocol.Mixed -> groupId
    }

    private fun com.wire.kalium.protobuf.messages.Calling.toPayload(
        transportConversationId: ConversationId,
        fallbackUserId: UserId,
        senderClientId: String,
        timestampSeconds: Long,
        messageId: String,
    ): IncomingCallingPayload {
        val host = qualifiedConversationId?.let { QualifiedID(it.id, it.domain) } ?: transportConversationId
        return IncomingCallingPayload(
            callHostConversationId = host,
            transportConversationId = transportConversationId,
            senderUserId = fallbackUserId,
            senderClientId = senderClientId,
            messageTimestampSeconds = timestampSeconds,
            content = content,
            isSelfMessage = fallbackUserId == identity.userId,
            messageId = messageId,
        )
    }

    private fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)
}

/** Required Proteus/self-client lifecycle handler. */
@ExperimentalKaliumServiceApi
public class WireProteusProtocolHandler(private val identity: ServiceIdentity) : EventHandler<WireDecryptedEvent> {
    override val requirement: EventHandlerRequirement = EventHandlerRequirement.REQUIRED

    override fun accepts(event: WireDecryptedEvent): Boolean = event.protocolOperations.any {
        it is WireProtocolOperation.ProteusMessageProcessed || it is WireProtocolOperation.SelfClientRemoved
    }

    override suspend fun handle(event: WireDecryptedEvent, context: EventHandlingContext): EventHandlingResult {
        val removed = event.protocolOperations.filterIsInstance<WireProtocolOperation.SelfClientRemoved>()
            .firstOrNull { it.clientId == identity.clientId }
        return if (removed == null) {
            EventHandlingResult.Handled
        } else {
            EventHandlingResult.Failed("The authenticated Wire service client was removed")
        }
    }
}

/** Required MLS protocol/group-state handler. */
@ExperimentalKaliumServiceApi
public class WireMlsProtocolHandler(
    private val crypto: WireServiceCryptoRuntime,
    private val contextProvider: ConversationContextProvider,
    private val stateStore: ConversationProtocolStateStore,
    private val conferenceMembership: WireConferenceMembership,
    private val crlHandler: ServiceCrlDistributionPointHandler,
    private val pendingProposalScheduler: WirePendingMlsCommitScheduler,
) : EventHandler<WireDecryptedEvent> {
    override val requirement: EventHandlerRequirement = EventHandlerRequirement.REQUIRED

    override fun accepts(event: WireDecryptedEvent): Boolean = event.protocolOperations.any {
        it is WireProtocolOperation.MlsMessageProcessed ||
                it is WireProtocolOperation.MlsWelcomeProcessed ||
                it is WireProtocolOperation.ProtocolUpdated ||
                it is WireProtocolOperation.MlsReset ||
                it is WireProtocolOperation.ConversationAccessRevoked ||
                it is WireProtocolOperation.CrlDistributionPoints ||
                it is WireProtocolOperation.MlsCommitPendingProposals
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    override suspend fun handle(event: WireDecryptedEvent, context: EventHandlingContext): EventHandlingResult = try {
        event.protocolOperations.forEach { operation ->
            when (operation) {
                is WireProtocolOperation.MlsMessageProcessed -> saveResolvedProtocol(operation)

                is WireProtocolOperation.MlsWelcomeProcessed -> savePreservingMixed(
                    operation.conversationId,
                    operation.groupId,
                    null,
                )

                is WireProtocolOperation.ProtocolUpdated -> when (operation.protocol) {
                    ConvProtocol.PROTEUS -> save(operation.conversationId, CallConversationProtocol.Proteus)
                    ConvProtocol.MLS,
                    ConvProtocol.MIXED -> when (val resolved = contextProvider.getForCall(operation.conversationId)) {
                        is ConversationContextResult.Failure -> error("Unable to resolve updated protocol: ${resolved.failure}")
                        is ConversationContextResult.Success -> save(operation.conversationId, resolved.context.protocol)
                    }
                }

                is WireProtocolOperation.MlsReset -> {
                    wipeGroup(operation.oldGroupId)
                    savePreservingMixed(operation.conversationId, operation.newGroupId, null)
                }

                is WireProtocolOperation.ConversationAccessRevoked -> {
                    when (val existing = stateStore.get(operation.conversationId)) {
                        is ConversationProtocolStateResult.Failure ->
                            throw existing.cause ?: IllegalStateException(existing.description)
                        is ConversationProtocolStateResult.Success -> when (val protocol = existing.value) {
                            is CallConversationProtocol.Mls -> wipeGroup(protocol.groupId)
                            is CallConversationProtocol.Mixed -> wipeGroup(protocol.groupId)
                            CallConversationProtocol.Proteus,
                            null -> Unit
                        }
                    }
                    when (val cleanup = conferenceMembership.cleanupForAccessLoss(operation.conversationId)) {
                        is CallingResult.Failure -> error("Unable to clean conference state after access loss: ${cleanup.failure}")
                        CallingResult.Success -> Unit
                    }
                    when (val removed = stateStore.remove(operation.conversationId)) {
                        is ConversationProtocolStateResult.Failure ->
                            throw removed.cause ?: IllegalStateException(removed.description)
                        is ConversationProtocolStateResult.Success -> Unit
                    }
                }

                is WireProtocolOperation.CrlDistributionPoints -> when (val result = crlHandler.handle(operation.values)) {
                    is CallingResult.Failure -> error("Unable to process MLS CRL distribution points: ${result.failure}")
                    CallingResult.Success -> Unit
                }

                is WireProtocolOperation.MlsCommitPendingProposals -> when (
                    val result = pendingProposalScheduler.schedule(operation.groupId.value, operation.commitAtEpochSeconds)
                ) {
                    is EncryptedServiceStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
                    is EncryptedServiceStateResult.Success -> Unit
                }

                is WireProtocolOperation.ProteusMessageProcessed,
                is WireProtocolOperation.SelfClientRemoved -> Unit
            }
        }
        EventHandlingResult.Handled
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        EventHandlingResult.Failed("Unable to persist required MLS protocol state", failure)
    }

    private suspend fun savePreservingMixed(conversationId: ConversationId, groupId: GroupID, epoch: ULong?) {
        val protocol = when (val existing = stateStore.get(conversationId)) {
            is ConversationProtocolStateResult.Failure -> throw existing.cause ?: IllegalStateException(existing.description)
            is ConversationProtocolStateResult.Success -> if (existing.value is CallConversationProtocol.Mixed) {
                CallConversationProtocol.Mixed(groupId, epoch)
            } else {
                CallConversationProtocol.Mls(groupId, epoch)
            }
        }
        save(conversationId, protocol)
    }

    private suspend fun saveResolvedProtocol(operation: WireProtocolOperation.MlsMessageProcessed) {
        val protocol = when (val result = contextProvider.getForCall(operation.conversationId)) {
            is ConversationContextResult.Failure -> error("Unable to resolve MLS protocol state: ${result.failure}")
            is ConversationContextResult.Success -> when (result.context.protocol) {
                CallConversationProtocol.Proteus -> error("An MLS message targeted a Proteus conversation")
                is CallConversationProtocol.Mls -> CallConversationProtocol.Mls(operation.groupId, operation.epoch)
                is CallConversationProtocol.Mixed -> CallConversationProtocol.Mixed(operation.groupId, operation.epoch)
            }
        }
        save(operation.conversationId, protocol)
    }

    private suspend fun save(conversationId: ConversationId, protocol: CallConversationProtocol) {
        when (val result = stateStore.save(conversationId, protocol)) {
            is ConversationProtocolStateResult.Failure -> throw result.cause ?: IllegalStateException(result.description)
            is ConversationProtocolStateResult.Success -> Unit
        }
    }

    private suspend fun wipeGroup(groupId: GroupID) {
        when (val cancelled = pendingProposalScheduler.cancel(groupId.value)) {
            is EncryptedServiceStateResult.Failure -> throw cancelled.cause ?: IllegalStateException(cancelled.description)
            is EncryptedServiceStateResult.Success -> Unit
        }
        crypto.withMls("service-wipe-revoked-mls-group") { mls ->
            if (mls.conversationExists(groupId.value)) mls.wipeConversation(groupId.value)
        }
    }
}

/** Calling payload extractor preserving aggregate and MLS batch order. */
@ExperimentalKaliumServiceApi
public object WireCallingPayloadExtractor : CallingPayloadExtractor<WireDecryptedEvent> {
    override fun extract(event: WireDecryptedEvent): List<IncomingCallingPayload> = event.callingPayloads
}

/** Service control messages remain AVS-owned until calling-team policy is confirmed. */
@ExperimentalKaliumServiceApi
public object ForwardAllCallingControlHandler : com.wire.kalium.calling.runtime.CallingControlHandler {
    override suspend fun handle(
        payload: IncomingCallingPayload,
        eventIdempotencyKey: String,
    ): com.wire.kalium.calling.runtime.CallingControlResult =
        com.wire.kalium.calling.runtime.CallingControlResult.ForwardToAvs
}

/** Removes a decrypted journal entry only after its durable delivery record is acknowledged. */
@ExperimentalKaliumServiceApi
public class JournaledEventDeliveryStateStore(
    private val delegate: EventDeliveryStateStore,
    private val journal: DecryptedEventJournal,
) : EventDeliveryStateStore {
    @Suppress("ReturnCount")
    override suspend fun loadState(): EventDeliveryStateResult<EventDeliveryState> {
        val state = when (val result = delegate.loadState()) {
            is EventDeliveryStateResult.Failure -> return result
            is EventDeliveryStateResult.Success -> result.value
        }
        val journalKeys = when (val result = journal.eventKeys()) {
            is EncryptedServiceStateResult.Failure -> return EventDeliveryStateResult.Failure(
                result.description,
                result.cause,
            )
            is EncryptedServiceStateResult.Success -> result.value
        }
        val pendingKeys = state.pendingAcknowledgements.mapTo(hashSetOf()) { it.key.value }
        journalKeys.filterNot { it in pendingKeys }.forEach { key ->
            val handled = when (val result = delegate.isHandled(EventIdempotencyKey(key))) {
                is EventDeliveryStateResult.Failure -> return result
                is EventDeliveryStateResult.Success -> result.value
            }
            if (handled) {
                when (val removed = journal.removeEvent(key)) {
                    is EncryptedServiceStateResult.Failure -> return EventDeliveryStateResult.Failure(
                        removed.description,
                        removed.cause,
                    )
                    is EncryptedServiceStateResult.Success -> Unit
                }
            }
        }
        return EventDeliveryStateResult.Success(state)
    }

    override suspend fun isHandled(key: EventIdempotencyKey): EventDeliveryStateResult<Boolean> = delegate.isHandled(key)

    override suspend fun recordHandled(
        key: EventIdempotencyKey,
        cursor: EventCursor,
        acknowledgement: EventAcknowledgement?,
        advancesCheckpoint: Boolean,
    ): EventDeliveryStateResult<Unit> = delegate.recordHandled(key, cursor, acknowledgement, advancesCheckpoint)

    override suspend fun recordAcknowledged(key: EventIdempotencyKey): EventDeliveryStateResult<Unit> {
        when (val result = delegate.recordAcknowledged(key)) {
            is EventDeliveryStateResult.Failure -> return result
            is EventDeliveryStateResult.Success -> Unit
        }
        return when (val removed = journal.removeEvent(key.value)) {
            is EncryptedServiceStateResult.Failure -> EventDeliveryStateResult.Failure(removed.description, removed.cause)
            is EncryptedServiceStateResult.Success -> EventDeliveryStateResult.Success(Unit)
        }
    }
}

@Suppress("MagicNumber")
private object WireDecryptedEventCodec {
    private const val VERSION = 3
    private const val LEGACY_VERSION = 1
    private const val MESSAGE_ID_VERSION = 2
    private const val MAX_COUNT = 10_000
    private const val MAX_STRING_BYTES = 4 * 1024 * 1024
    private val inProgressMarker = byteArrayOf(0x4b, 0x41, 0x4c, 0x49, 0x55, 0x4d, 0x01)

    fun inProgress(): ByteArray = inProgressMarker.copyOf()

    fun isInProgress(bytes: ByteArray): Boolean = bytes.contentEquals(inProgressMarker)

    fun encode(event: WireDecryptedEvent): ByteArray = ByteArrayOutputStream().let { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeInt(event.callingPayloads.size)
            event.callingPayloads.forEach { output.writePayload(it) }
            output.writeInt(event.protocolOperations.size)
            event.protocolOperations.forEach { output.writeOperation(it) }
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): WireDecryptedEvent = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val version = input.readInt()
        require(version in LEGACY_VERSION..VERSION) { "Unsupported decrypted event journal version" }
        val calls = List(input.readCount()) { input.readPayload(version) }
        val operations = List(input.readCount()) { input.readOperation() }
        require(input.available() == 0) { "Decrypted event journal contains trailing data" }
        WireDecryptedEvent(calls, operations)
    }

    private fun DataOutputStream.writePayload(payload: IncomingCallingPayload) {
        writeQualifiedId(payload.callHostConversationId)
        writeQualifiedId(payload.transportConversationId)
        writeQualifiedId(payload.senderUserId)
        writeString(payload.senderClientId)
        writeLong(payload.messageTimestampSeconds)
        writeString(payload.content)
        writeBoolean(payload.isSelfMessage)
        writeBoolean(payload.messageId != null)
        payload.messageId?.let { writeString(it) }
    }

    private fun DataInputStream.readPayload(version: Int): IncomingCallingPayload = IncomingCallingPayload(
        callHostConversationId = readQualifiedId(),
        transportConversationId = readQualifiedId(),
        senderUserId = readQualifiedId(),
        senderClientId = readString(),
        messageTimestampSeconds = readLong(),
        content = readString(),
        isSelfMessage = readBoolean(),
        messageId = if (version >= MESSAGE_ID_VERSION && readBoolean()) readString() else null,
    )

    private fun DataOutputStream.writeOperation(operation: WireProtocolOperation) {
        when (operation) {
            WireProtocolOperation.ProteusMessageProcessed -> writeByte(1)
            is WireProtocolOperation.MlsMessageProcessed -> {
                writeByte(2)
                writeQualifiedId(operation.conversationId)
                writeString(operation.groupId.value)
                writeLong(operation.epoch.toLong())
            }
            is WireProtocolOperation.MlsWelcomeProcessed -> {
                writeByte(3)
                writeQualifiedId(operation.conversationId)
                writeString(operation.groupId.value)
            }
            is WireProtocolOperation.ProtocolUpdated -> {
                writeByte(4)
                writeQualifiedId(operation.conversationId)
                writeByte(
                    when (operation.protocol) {
                        ConvProtocol.PROTEUS -> 1
                        ConvProtocol.MLS -> 2
                        ConvProtocol.MIXED -> 3
                    },
                )
            }
            is WireProtocolOperation.MlsReset -> {
                writeByte(5)
                writeQualifiedId(operation.conversationId)
                writeString(operation.oldGroupId.value)
                writeString(operation.newGroupId.value)
            }
            is WireProtocolOperation.SelfClientRemoved -> {
                writeByte(6)
                writeString(operation.clientId)
            }
            is WireProtocolOperation.ConversationAccessRevoked -> {
                writeByte(7)
                writeQualifiedId(operation.conversationId)
            }
            is WireProtocolOperation.CrlDistributionPoints -> {
                writeByte(8)
                writeInt(operation.values.size)
                operation.values.forEach { writeString(it) }
            }
            is WireProtocolOperation.MlsCommitPendingProposals -> {
                writeByte(9)
                writeString(operation.groupId.value)
                writeLong(operation.commitAtEpochSeconds)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun DataInputStream.readOperation(): WireProtocolOperation = when (readUnsignedByte()) {
        1 -> WireProtocolOperation.ProteusMessageProcessed
        2 -> WireProtocolOperation.MlsMessageProcessed(readQualifiedId(), GroupID(readString()), readLong().toULong())
        3 -> WireProtocolOperation.MlsWelcomeProcessed(readQualifiedId(), GroupID(readString()))
        4 -> WireProtocolOperation.ProtocolUpdated(
            readQualifiedId(),
            when (readUnsignedByte()) {
                1 -> ConvProtocol.PROTEUS
                2 -> ConvProtocol.MLS
                3 -> ConvProtocol.MIXED
                else -> error("Unknown conversation protocol")
            },
        )
        5 -> WireProtocolOperation.MlsReset(readQualifiedId(), GroupID(readString()), GroupID(readString()))
        6 -> WireProtocolOperation.SelfClientRemoved(readString())
        7 -> WireProtocolOperation.ConversationAccessRevoked(readQualifiedId())
        8 -> WireProtocolOperation.CrlDistributionPoints(List(readCount()) { readString() })
        9 -> WireProtocolOperation.MlsCommitPendingProposals(GroupID(readString()), readLong())
        else -> error("Unknown decrypted event operation")
    }

    private fun DataOutputStream.writeQualifiedId(id: QualifiedID) {
        writeString(id.value)
        writeString(id.domain)
    }

    private fun DataInputStream.readQualifiedId(): QualifiedID = QualifiedID(readString(), readString())

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.encodeToByteArray()
        require(encoded.size <= MAX_STRING_BYTES) { "Journal string is too large" }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES) { "Journal string has an invalid size" }
        return ByteArray(size).also(::readFully).decodeToString()
    }

    private fun DataInputStream.readCount(): Int = readInt().also {
        require(it in 0..MAX_COUNT) { "Journal collection has an invalid size" }
    }
}
