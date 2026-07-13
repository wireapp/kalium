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
@file:Suppress("MagicNumber")

package com.wire.kalium.notificationextension

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.receiving.DecodedMessageContent
import com.wire.kalium.messagecontent.NotificationContent
import com.wire.kalium.messagecontent.NotificationContentExtractionResult
import com.wire.kalium.messagecontent.NotificationContentExtractorImpl
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoderImpl
import com.wire.kalium.notificationinbox.InboxReadResult as StoreReadResult
import com.wire.kalium.notificationinbox.InboxScope
import com.wire.kalium.notificationinbox.NotificationInboxFailure
import com.wire.kalium.notificationinbox.NotificationInboxLimits
import com.wire.kalium.notificationinbox.NotificationInboxStore
import com.wire.kalium.notificationinbox.NotificationState
import com.wire.kalium.notificationinbox.RawEnvelopeDeliverySource
import com.wire.kalium.notificationinbox.SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID
import com.wire.kalium.notificationinbox.SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
import com.wire.kalium.notificationinbox.SyntheticNotificationInboxOpenResult
import com.wire.kalium.notificationinbox.SyntheticPlaintextNotificationInboxFactory
import com.wire.kalium.notificationinbox.fallbackChildIdempotencyKey
import com.wire.kalium.notificationsync.BoundedNotificationSyncEngine
import com.wire.kalium.notificationsync.NotificationEventKey
import com.wire.kalium.notificationsync.NotificationSyncSession
import com.wire.kalium.notificationsync.NotificationSyncTransport
import com.wire.kalium.notificationsync.NotificationSyncCursor
import com.wire.kalium.notificationsync.NotificationTransportFrame
import com.wire.kalium.notificationsync.NotificationTransportMode
import com.wire.kalium.notificationsync.NotificationTransportReceiveResult
import com.wire.kalium.notificationsync.OpenSessionResult
import com.wire.kalium.notificationsync.RawNotificationEvent
import com.wire.kalium.notificationsync.TransportAckResult
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import platform.Foundation.NSFileManager
import kotlin.concurrent.atomics.AtomicInt

/** Scalar-only result for the disposable Swift/simulator M7 probe. */
@Suppress("LongParameterList")
public data class NotificationExtensionFrameworkProbeResult(
    public val passed: Boolean,
    public val completionCount: Int,
    public val immediateExpirationCompletion: Boolean,
    public val stageBeforeAck: Boolean,
    public val lockHeldForStorage: Boolean,
    public val storeClosedBeforeRelease: Boolean,
    public val exactProto: Boolean,
    public val genericFallback: Boolean,
    public val receivingAndExtractionLinked: Boolean,
    public val avsBridgeUnderLock: Boolean,
    public val avsFacadeReturned: Boolean,
    public val productionAvailable: Boolean,
    public val realNetwork: Boolean,
    public val realCrypto: Boolean,
    public val realAvs: Boolean,
    public val detail: String
)

/**
 * Local synthetic feasibility evidence only. No backend, credential, real account, or real message
 * may be supplied to this probe, and its plaintext M6 database is never a production constructor.
 */
public class NotificationExtensionFrameworkProbe {
    public suspend fun run(
        sharedRoot: String,
        callProcessor: NotificationExtensionCallProcessor
    ): NotificationExtensionFrameworkProbeResult = runCatching {
        execute(sharedRoot, callProcessor)
    }.getOrElse { failure ->
        failedProbe(failure)
    }

    @Suppress("LongMethod")
    private suspend fun execute(
        sharedRoot: String,
        callProcessor: NotificationExtensionCallProcessor
    ): NotificationExtensionFrameworkProbeResult {
        check(ensureDirectory(sharedRoot)) { "phase=create-root" }
        val evidence = MutableProbeEvidence()
        val timeline = mutableListOf<String>()
        val handoffDirectory = "$sharedRoot/m7-synthetic-${Clock.System.now().toEpochMilliseconds()}"
        val processLockFactory = AppleProcessLockFactory(sharedRoot)
        val provider = SyntheticStoreProvider(
            factory = SyntheticPlaintextNotificationInboxFactory(handoffDirectory, PROBE_LIMITS),
            processLockFactory = processLockFactory,
            timeline = timeline,
            evidence = evidence
        )
        val inbox = NotificationInboxSyncAdapter(
            provider = provider,
            deliverySource = RawEnvelopeDeliverySource.SYNTHETIC_FEASIBILITY
        )
        val transport = SyntheticTransport(provider, processLockFactory, evidence)
        val eventProcessor = NotificationInboxEventProcessor(
            scope = PROBE_INBOX_SCOPE,
            provider = provider,
            handler = { event ->
                receiveSyntheticEvent(event, evidence, processLockFactory, callProcessor)
            }
        )
        val runtime = BoundedNotificationExtensionRuntime {
            BoundedNotificationSyncEngine(
                leaseCoordinator = AppleNotificationSyncLeaseCoordinator(sharedRoot, provider::close),
                inbox = inbox,
                transport = transport,
                eventProcessor = eventProcessor
            )
        }
        val completionCount = AtomicInt(0)
        val completion = CompletableDeferred<NotificationExtensionResult>()
        val component = NotificationExtension(runtime)
        val request = probeRequest(Clock.System.now().toEpochMilliseconds() + PROBE_DEADLINE_MILLIS)
        val handle = component.begin(request) { result ->
            completionCount.fetchAndAdd(1)
            completion.complete(result)
        }
        val result = completion.await()
        handle.cancelForExpiration()
        handle.cancelForExpiration()
        handle.cancel()
        yield()
        component.close()
        check(result.status == NotificationExtensionStatus.COMPLETE) {
            "phase=normal-result status=${result.status} reason=${result.reason}"
        }
        check(completionCount.load() == 1) { "phase=normal-completion count=${completionCount.load()}" }

        val storedChild = verifyStoredChild(processLockFactory, handoffDirectory)
        timeline += "lock-reacquired"
        val storeClosedBeforeRelease = timeline.indexOf("store-closed") in 0 until timeline.indexOf("lock-reacquired")
        val expirationComplete = verifyImmediateExpirationCompletion()
        val construction = NotificationExtensionFactory.createProduction(
            NotificationExtensionHostConfiguration(sharedRoot)
        )

        val genericFallback = result.shouldUsePrivacyPreservingFallback && storedChild.contentDetailsSuppressed
        val passed = completionCount.load() == 1 && expirationComplete && evidence.stageBeforeAck &&
                evidence.lockHeldForStorage && storeClosedBeforeRelease && storedChild.exactProto &&
                genericFallback && evidence.receivingAndExtractionLinked && evidence.avsBridgeUnderLock &&
                evidence.avsFacadeReturned && !construction.isAvailable
        return NotificationExtensionFrameworkProbeResult(
            passed = passed,
            completionCount = completionCount.load(),
            immediateExpirationCompletion = expirationComplete,
            stageBeforeAck = evidence.stageBeforeAck,
            lockHeldForStorage = evidence.lockHeldForStorage,
            storeClosedBeforeRelease = storeClosedBeforeRelease,
            exactProto = storedChild.exactProto,
            genericFallback = genericFallback,
            receivingAndExtractionLinked = evidence.receivingAndExtractionLinked,
            avsBridgeUnderLock = evidence.avsBridgeUnderLock,
            avsFacadeReturned = evidence.avsFacadeReturned,
            productionAvailable = construction.isAvailable,
            realNetwork = false,
            realCrypto = false,
            realAvs = false,
            detail = "complete=${result.status == NotificationExtensionStatus.COMPLETE}; " +
                    "immediateCancelIterations=$IMMEDIATE_CANCEL_ITERATIONS; splitAvs=true; policySnapshot=false"
        )
    }

    private suspend fun receiveSyntheticEvent(
        event: com.wire.kalium.notificationsync.StagedNotificationEvent,
        evidence: MutableProbeEvidence,
        processLockFactory: AppleProcessLockFactory,
        callProcessor: NotificationExtensionCallProcessor
    ): ReceiveOnlyStagedEventResult {
        check(event.key.serverEventId == PROBE_EVENT_ID) { "phase=receive-event-id" }
        val decoder = ProtobufMessageContentDecoderImpl(UserId("synthetic-self", "example.invalid"))
        val receiveAdapter = ReceiveOnlyProtobufDecoderAdapter(decoder)
        val received = receiveAdapter.decode(PROBE_PROTO)
        check(received is DecodedMessageContent.Application) { "phase=receive-decode" }
        val extraction = NotificationContentExtractorImpl().extract(received.content)
        val text = (extraction as? NotificationContentExtractionResult.Candidate)?.content as? NotificationContent.Text
        check(text?.value == PROBE_TEXT) { "phase=extract-text" }
        check(received.content.serializedContent.contentEquals(PROBE_PROTO)) { "phase=exact-decoded-proto" }
        evidence.receivingAndExtractionLinked = true
        val lockHeldBeforeAvs = processLockIsHeld(processLockFactory)
        val avsStatus = callProcessor.process(listOf(probeCallEvent()))
        val lockHeldAfterAvs = processLockIsHeld(processLockFactory)
        evidence.avsBridgeUnderLock = lockHeldBeforeAvs && lockHeldAfterAvs
        // The synthetic payload is intentionally malformed. Returning a classified failure proves
        // that Swift synchronously called the AVS façade without pretending this was a real event.
        evidence.avsFacadeReturned = avsStatus == NotificationExtensionCallProcessingStatus.TERMINAL_FAILURE
        return ReceiveOnlyStagedEventResult.Children(
            listOf(
                syntheticReceiveChild(
                    scope = PROBE_INBOX_SCOPE,
                    parentServerEventId = PROBE_EVENT_ID,
                    idempotencyKey = fallbackChildIdempotencyKey(PROBE_EVENT_ID, 0),
                    decryptedProto = received.content.serializedContent
                )
            )
        )
    }

    private fun processLockIsHeld(processLockFactory: AppleProcessLockFactory): Boolean =
        when (val result = processLockFactory.tryAcquire(PROBE_ACCOUNT_ID, PROBE_CLIENT_ID)) {
            ProcessLockAcquireResult.Unavailable -> true
            is ProcessLockAcquireResult.Acquired -> {
                result.lease.release()
                false
            }
            else -> false
        }

    private suspend fun verifyStoredChild(
        processLockFactory: AppleProcessLockFactory,
        handoffDirectory: String
    ): StoredChildEvidence {
        val lease = when (val result = processLockFactory.tryAcquire(PROBE_ACCOUNT_ID, PROBE_CLIENT_ID)) {
            is ProcessLockAcquireResult.Acquired -> result.lease
            else -> error("M7 probe could not reacquire process lock: $result")
        }
        try {
            val store = when (
                val result = SyntheticPlaintextNotificationInboxFactory(handoffDirectory, PROBE_LIMITS).open()
            ) {
                is SyntheticNotificationInboxOpenResult.Opened -> result.store
                is SyntheticNotificationInboxOpenResult.Failure -> error("M7 probe reopen failed: ${result.reason}")
            }
            try {
                val children = when (val result = store.readPendingImportChildren(PROBE_INBOX_SCOPE, 1)) {
                    is StoreReadResult.Success -> result.value.children
                    is StoreReadResult.StorageFailure -> error("M7 probe child read failed: ${result.reason}")
                }
                val child = children.singleOrNull()
                return StoredChildEvidence(
                    exactProto = child?.decryptedProto?.contentEquals(PROBE_PROTO) == true,
                    contentDetailsSuppressed = child?.notificationState == NotificationState.SUPPRESSED
                )
            } finally {
                store.close()
            }
        } finally {
            lease.release()
        }
    }

    private suspend fun verifyImmediateExpirationCompletion(): Boolean {
        repeat(IMMEDIATE_CANCEL_ITERATIONS) {
            val completionCount = AtomicInt(0)
            val completion = CompletableDeferred<NotificationExtensionResult>()
            val component = NotificationExtension(NotificationExtensionRuntime { awaitCancellation() })
            val handle = component.begin(probeRequest(Long.MAX_VALUE)) { result ->
                completionCount.fetchAndAdd(1)
                completion.complete(result)
            }
            handle.cancelForExpiration()
            handle.cancelForExpiration()
            handle.cancel()
            val result = completion.await()
            yield()
            component.close()
            check(completionCount.load() == 1) { "phase=expiration-count iteration=$it" }
            check(result.status == NotificationExtensionStatus.DEADLINE_REACHED) {
                "phase=expiration-status iteration=$it status=${result.status}"
            }
            check(result.reason == NotificationExtensionReason.DEADLINE) {
                "phase=expiration-reason iteration=$it reason=${result.reason}"
            }
        }
        return true
    }
}

private class SyntheticStoreProvider(
    private val factory: SyntheticPlaintextNotificationInboxFactory,
    private val processLockFactory: AppleProcessLockFactory,
    private val timeline: MutableList<String>,
    private val evidence: MutableProbeEvidence
) : NotificationInboxStoreProvider {
    private var store: NotificationInboxStore? = null
    private var closed = false

    @Suppress("ReturnCount")
    override suspend fun get(): NotificationInboxStoreAccessResult {
        store?.let { return NotificationInboxStoreAccessResult.Opened(it) }
        if (closed) return NotificationInboxStoreAccessResult.TerminalFailure
        evidence.lockHeldForStorage = when (
            val attempt = processLockFactory.tryAcquire(PROBE_ACCOUNT_ID, PROBE_CLIENT_ID)
        ) {
            ProcessLockAcquireResult.Unavailable -> true
            is ProcessLockAcquireResult.Acquired -> {
                attempt.lease.release()
                false
            }
            else -> false
        }
        if (!evidence.lockHeldForStorage) return NotificationInboxStoreAccessResult.TerminalFailure
        return when (val result = factory.open()) {
            is SyntheticNotificationInboxOpenResult.Opened -> {
                store = result.store
                timeline += "store-opened"
                NotificationInboxStoreAccessResult.Opened(result.store)
            }
            is SyntheticNotificationInboxOpenResult.Failure -> if (
                result.reason == NotificationInboxFailure.STORAGE_UNAVAILABLE
            ) {
                NotificationInboxStoreAccessResult.RetryableFailure
            } else {
                NotificationInboxStoreAccessResult.TerminalFailure
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        store?.close()
        store = null
        timeline += "store-closed"
    }
}

private class SyntheticTransport(
    private val provider: NotificationInboxStoreProvider,
    private val processLockFactory: AppleProcessLockFactory,
    private val evidence: MutableProbeEvidence
) : NotificationSyncTransport {
    override suspend fun openSession(
        request: com.wire.kalium.notificationsync.NotificationTransportSessionRequest
    ): OpenSessionResult {
        check(request.scope.accountId == PROBE_ACCOUNT_ID && request.scope.clientId == PROBE_CLIENT_ID) {
            "phase=transport-scope"
        }
        check(processLockFactory.tryAcquire(PROBE_ACCOUNT_ID, PROBE_CLIENT_ID) == ProcessLockAcquireResult.Unavailable) {
            "phase=transport-lock"
        }
        return OpenSessionResult.Opened(SyntheticSession(provider, evidence))
    }
}

private class SyntheticSession(
    private val provider: NotificationInboxStoreProvider,
    private val evidence: MutableProbeEvidence
) : NotificationSyncSession {
    override val mode: NotificationTransportMode = NotificationTransportMode.CONSUMABLE
    private var frameIndex = 0
    private var closed = false

    override suspend fun receive(): NotificationTransportReceiveResult {
        val frame = when (frameIndex++) {
            0 -> NotificationTransportFrame.Event(
                event = RawNotificationEvent(
                    key = NotificationEventKey(PROBE_EVENT_ID),
                    rawEnvelope = PROBE_RAW_ENVELOPE,
                    isTransient = false,
                    cursor = NotificationSyncCursor(PROBE_CURSOR)
                ),
                deliveryTag = EVENT_DELIVERY_TAG
            )
            else -> NotificationTransportFrame.SynchronizationMarker(PROBE_MARKER, MARKER_DELIVERY_TAG)
        }
        return NotificationTransportReceiveResult.Received(frame)
    }

    override suspend fun enqueueTransportAck(deliveryTag: ULong): TransportAckResult {
        if (deliveryTag == EVENT_DELIVERY_TAG) {
            val store = (provider.get() as? NotificationInboxStoreAccessResult.Opened)?.store
                ?: return TransportAckResult.RejectedTerminal
            evidence.stageBeforeAck = when (val result = store.readPendingReceive(PROBE_INBOX_SCOPE, 1)) {
                is StoreReadResult.Success -> result.value.events.singleOrNull()?.serverEventId == PROBE_EVENT_ID
                is StoreReadResult.StorageFailure -> false
            }
        }
        return if (deliveryTag == EVENT_DELIVERY_TAG && !evidence.stageBeforeAck) {
            TransportAckResult.RejectedTerminal
        } else {
            TransportAckResult.AcceptedByLocalWriter
        }
    }

    override fun close() {
        closed = true
    }
}

private class MutableProbeEvidence {
    var stageBeforeAck: Boolean = false
    var lockHeldForStorage: Boolean = false
    var receivingAndExtractionLinked: Boolean = false
    var avsBridgeUnderLock: Boolean = false
    var avsFacadeReturned: Boolean = false
}

private data class StoredChildEvidence(
    val exactProto: Boolean,
    val contentDetailsSuppressed: Boolean
)

private fun probeCallEvent(): NotificationExtensionCallEvent = NotificationExtensionCallEvent(
    payload = "{}",
    currentTimeSeconds = 1,
    messageTimeSeconds = 1,
    conversationId = "synthetic-avs-conversation",
    senderUserId = "synthetic-avs-sender",
    senderClientId = "synthetic-avs-sender-client",
    conversationType = 0
)

private fun probeRequest(deadlineEpochMillis: Long): NotificationExtensionRequest = NotificationExtensionRequest(
    accountId = PROBE_ACCOUNT_ID,
    clientId = PROBE_CLIENT_ID,
    markerId = PROBE_MARKER,
    absoluteDeadlineEpochMillis = deadlineEpochMillis,
    maxTransportFrames = 2,
    maxEventsToStage = 1,
    maxDrainBatches = 1,
    maxEventsPerDrainBatch = 1,
    deadlineSafetyMarginMillis = 100
)

private fun ensureDirectory(path: String): Boolean = NSFileManager.defaultManager.createDirectoryAtPath(
    path = path,
    withIntermediateDirectories = true,
    attributes = null,
    error = null
)

private fun failedProbe(failure: Throwable): NotificationExtensionFrameworkProbeResult =
    NotificationExtensionFrameworkProbeResult(
        passed = false,
        completionCount = 0,
        immediateExpirationCompletion = false,
        stageBeforeAck = false,
        lockHeldForStorage = false,
        storeClosedBeforeRelease = false,
        exactProto = false,
        genericFallback = false,
        receivingAndExtractionLinked = false,
        avsBridgeUnderLock = false,
        avsFacadeReturned = false,
        productionAvailable = false,
        realNetwork = false,
        realCrypto = false,
        realAvs = false,
        detail = failure.message ?: failure::class.simpleName.orEmpty()
    )

private val PROBE_INBOX_SCOPE = InboxScope(
    SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID,
    SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
)
private val PROBE_LIMITS = NotificationInboxLimits(
    maxIdentifierUtf8Bytes = 256,
    maxCursorUtf8Bytes = 256,
    maxReasonUtf8Bytes = 256,
    maxRawEnvelopeBytes = 65_536,
    maxDecryptedProtoBytes = 65_536,
    maxBatchBlobBytes = 262_144,
    maxRowsPerRead = 16,
    maxChildrenPerEvent = 8,
    maxRetryCount = 3
)
private val PROBE_RAW_ENVELOPE =
    "{\"type\":\"synthetic-m7\",\"unknown\":{\"future\":true}}".encodeToByteArray()
private val PROBE_PROTO = GenericMessage(
    messageId = "synthetic-m7-message",
    content = GenericMessage.Content.Text(Text(content = PROBE_TEXT))
).encodeToByteArray()
private const val PROBE_ACCOUNT_ID = SYNTHETIC_NOTIFICATION_INBOX_ACCOUNT_ID
private const val PROBE_CLIENT_ID = SYNTHETIC_NOTIFICATION_INBOX_CLIENT_ID
private const val PROBE_EVENT_ID = "synthetic-m7-event"
private const val PROBE_CURSOR = "synthetic-m7-cursor"
private const val PROBE_MARKER = "synthetic-m7-marker"
private const val PROBE_TEXT = "synthetic local notification"
private const val PROBE_DEADLINE_MILLIS = 10_000L
private const val IMMEDIATE_CANCEL_ITERATIONS = 32
private const val EVENT_DELIVERY_TAG = 71UL
private const val MARKER_DELIVERY_TAG = 72UL
