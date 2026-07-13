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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.nse.feasibility

import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.messaging.receiving.DecodedMessageContent
import com.wire.kalium.messaging.receiving.MessageContentDecoder
import com.wire.kalium.messaging.receiving.MessageContentResolverImpl
import com.wire.kalium.messaging.receiving.MessageContentResolution
import com.wire.kalium.messaging.receiving.MlsEncryptedMessage
import com.wire.kalium.messaging.receiving.MlsMessageDecryptorImpl
import com.wire.kalium.messaging.receiving.ProteusEncryptedMessage
import com.wire.kalium.messaging.receiving.ProteusMessageDecryptorImpl
import com.wire.kalium.messagecontent.DecodedProtobufContent
import com.wire.kalium.messagecontent.NotificationContent
import com.wire.kalium.messagecontent.NotificationContentExtractionResult
import com.wire.kalium.messagecontent.NotificationContentExtractorImpl
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoderImpl
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Quote
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.synccoordination.AppleProcessLockFactory
import com.wire.kalium.synccoordination.ProcessLockAcquireResult
import com.wire.kalium.synccoordination.ProcessLockLease
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.CLOCK_MONOTONIC
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.close
import platform.posix.clock_gettime
import platform.posix.errno
import platform.posix.ftruncate
import platform.posix.lseek
import platform.posix.open
import platform.posix.read
import platform.posix.strerror
import platform.posix.timespec
import platform.posix.write

/**
 * Disposable Apple-only probes for ADR 0010 Milestone 1.
 *
 * This is deliberately not a production synchronization API. It accepts a caller-supplied root so
 * an iOS host can pass an App Group URL rather than relying on a process sandbox home directory.
 */
public class NseFeasibilityProbe {

    /** Uses only locally constructed synthetic bytes in the explicitly plaintext M6 store. */
    public suspend fun probeSyntheticNotificationInbox(sharedRoot: String): FeasibilityProbeResult =
        measureSuspending("synthetic-notification-inbox") {
            runSyntheticNotificationInboxProbe(sharedRoot)
        }

    /** Exercises the M8 post-main-commit contract without a product native database. */
    public suspend fun probeSyntheticForegroundImporter(sharedRoot: String): FeasibilityProbeResult =
        measureSuspending("synthetic-foreground-importer") {
            runSyntheticForegroundImportContractProbe(sharedRoot)
        }

    /** Exercises repository-owned M9 lifecycle, cleanup, recovery, and footprint contracts. */
    public suspend fun probeNotificationInboxHardening(sharedRoot: String): FeasibilityProbeResult =
        measureSuspending("notification-inbox-hardening") {
            runNotificationInboxHardeningProbe(sharedRoot)
        }

    /** Locally constructs protobufs to prove decode/extraction linkage without account state. */
    public fun probeMessageContentExtraction(): FeasibilityProbeResult = measure("message-content-extraction") {
        val textBytes = GenericMessage(
            messageId = "probe-message",
            content = GenericMessage.Content.Text(
                Text(
                    content = "local probe",
                    quote = Quote(quotedMessageId = "quoted-message")
                )
            )
        ).encodeToByteArray()
        val decoder = ProtobufMessageContentDecoderImpl(UserId("probe-user", "example.invalid"))
        val decoded = decoder.decode(textBytes)
        val extraction = NotificationContentExtractorImpl().extract(decoded)
        val textCandidate =
            (extraction as? NotificationContentExtractionResult.Candidate)?.content as? NotificationContent.Text
        check(textCandidate?.value == "local probe")
        check(textCandidate.quotedMessageId == "quoted-message")
        check(decoded.serializedContent.contentEquals(textBytes))

        val unknownBytes = GenericMessage(
            messageId = "probe-unknown",
            unknownStrategy = GenericMessage.UnknownStrategy.IGNORE
        ).encodeToByteArray()
        val unknown = decoder.decode(unknownBytes)
        check(unknown.classification == DecodedProtobufContent.Classification.UNSUPPORTED)
        check((unknown.content as? ProtoContent.Readable)?.messageContent == MessageContent.Ignored)
        check(NotificationContentExtractorImpl().extract(unknown) is NotificationContentExtractionResult.Unsupported)

        "text=true; quote=true; exactBytes=true; unsupportedSafe=true; policyEvaluation=false"
    }

    /**
     * Proves that the receive-only contracts are present in this framework without decrypting a
     * real message or mutating CoreCrypto state.
     */
    public fun probeReceivingContractLinkage(): FeasibilityProbeResult = measure("receiving-contract-linkage") {
        val proteusInput = ProteusEncryptedMessage(
            sessionId = CryptoSessionId(
                userId = CryptoQualifiedID("probe-user", "example.invalid"),
                cryptoClientId = CryptoClientId("probe-client")
            ),
            encryptedMessage = byteArrayOf(1)
        )
        val mlsInput = MlsEncryptedMessage(groupId = "probe-group", encryptedMessage = byteArrayOf(2))
        val decoder = MessageContentDecoder<String> { DecodedMessageContent.Application("linked") }
        val resolved = MessageContentResolverImpl().resolveProteusContent(
            decryptedMessage = byteArrayOf(RECEIVING_LINKAGE_CONTENT_BYTE),
            encryptedExternalContent = null,
            decoder = decoder
        )
        check(resolved is MessageContentResolution.Success && resolved.message.content == "linked")

        "proteusInput=${proteusInput::class.simpleName}; mlsInput=${mlsInput::class.simpleName}; " +
                "proteusDecryptor=${ProteusMessageDecryptorImpl::class.simpleName}; " +
                "mlsDecryptor=${MlsMessageDecryptorImpl::class.simpleName}; realDecryption=false"
    }

    public fun probeSharedRoot(sharedRoot: String): FeasibilityProbeResult = measure("shared-root") {
        val accountRoot = accountRoot(sharedRoot, PROBE_ACCOUNT)
        requireDirectory(accountRoot)

        val markerPath = "$accountRoot/path-probe.marker"
        val marker = "kalium-nse-shared-root-probe".encodeToByteArray()
        writeFile(markerPath, marker)
        val restored = readFile(markerPath, marker.size)
        check(restored.contentEquals(marker)) { "Marker round-trip did not preserve bytes" }

        "callerRoot=${standardizedPath(sharedRoot)} accountRoot=$accountRoot"
    }

    public fun tryAcquireProcessLock(
        sharedRoot: String,
        accountId: String = PROBE_ACCOUNT,
        clientId: String = PROBE_CLIENT
    ): FeasibilityLockAttempt {
        val startedAt = nowNanos()
        return when (val result = AppleProcessLockFactory(sharedRoot).tryAcquire(accountId, clientId)) {
            is ProcessLockAcquireResult.Acquired -> FeasibilityLockAttempt(
                acquired = true,
                elapsedNanos = nowNanos() - startedAt,
                detail = "exclusive non-blocking Apple lock acquired",
                lock = FeasibilityProcessLock(result.lease)
            )
            ProcessLockAcquireResult.Unavailable -> FeasibilityLockAttempt(
                acquired = false,
                elapsedNanos = nowNanos() - startedAt,
                detail = "exclusive non-blocking Apple lock unavailable",
                lock = null
            )
            is ProcessLockAcquireResult.RetryableFailure -> FeasibilityLockAttempt(
                acquired = false,
                elapsedNanos = nowNanos() - startedAt,
                detail = "retryable Apple lock failure=${result.reason}",
                lock = null
            )
            is ProcessLockAcquireResult.TerminalFailure -> FeasibilityLockAttempt(
                acquired = false,
                elapsedNanos = nowNanos() - startedAt,
                detail = "terminal Apple lock failure=${result.reason}",
                lock = null
            )
        }
    }

    /** Exercises the target platform's Apple primitive in-process, including idempotent release. */
    public fun probeProductionProcessLockLifecycle(sharedRoot: String): FeasibilityProbeResult =
        measure("apple-process-lock-lifecycle") {
            requireDirectory(sharedRoot)
            val factory = AppleProcessLockFactory(sharedRoot)
            val first = factory.tryAcquire(PROBE_ACCOUNT, PROBE_CLIENT)
            check(first is ProcessLockAcquireResult.Acquired) { "first acquisition=$first" }
            try {
                check(factory.tryAcquire(PROBE_ACCOUNT, PROBE_CLIENT) == ProcessLockAcquireResult.Unavailable)
            } finally {
                first.lease.release()
                first.lease.release()
            }

            val reacquired = factory.tryAcquire(PROBE_ACCOUNT, PROBE_CLIENT)
            check(reacquired is ProcessLockAcquireResult.Acquired) { "reacquisition=$reacquired" }
            reacquired.lease.release()

            val expectedLockPath =
                "${standardizedPath(sharedRoot)}/kalium-nse/v1/$PROBE_ACCOUNT_CLIENT_DIGEST/sync.lock"
            check(NSFileManager.defaultManager.fileExistsAtPath(expectedLockPath)) {
                "expected versioned lock file was not retained"
            }

            "acquired=true; contention=unavailable; releaseTwice=true; reacquired=true; " +
                    "fileRetained=true; platformImplementationExercised=true; realProcesses=false"
        }

    /**
     * Opens and closes the same CoreCrypto database twice in sequence. It does not prove concurrent
     * multi-process safety or state transfer between an app and an NSE.
     */
    public suspend fun probeSequentialCoreCryptoOpenClose(sharedRoot: String): FeasibilityProbeResult =
        measureSuspending("corecrypto-sequential-open-close") {
            val coreCryptoRoot = "${accountRoot(sharedRoot, PROBE_ACCOUNT)}/corecrypto"
            requireDirectory(coreCryptoRoot)

            openThenCloseCoreCrypto(coreCryptoRoot)
            openThenCloseCoreCrypto(coreCryptoRoot)
            "opened and closed the same CoreCrypto path twice; path=$coreCryptoRoot"
        }

    /**
     * Constructs and closes the Darwin HTTP engine while retaining a compile-time reference to the
     * consumable notification WebSocket API. Backend connectivity still requires credentials and a
     * reachable test backend supplied by the iOS host.
     */
    public fun probeNotificationWebSocketLinkage(): FeasibilityProbeResult = measure("notification-websocket-linkage") {
        val engine = defaultHttpEngine(
            serverConfigDTOApiProxy = null,
            proxyCredentials = null,
            ignoreSSLCertificates = false,
            certificatePinning = emptyMap()
        )
        try {
            "engine=${engine::class.simpleName} api=${NotificationApi::consumeLiveEvents.name}"
        } finally {
            engine.close()
        }
    }

    /**
     * Documents the current repository limitation while also compiling the exact Apple persistence
     * configuration and caller-root types that a future NSE assembly would receive.
     */
    public fun inspectCurrentApplePersistenceSurface(
        sharedRoot: String,
        keychainServiceName: String
    ): FeasibilityProbeResult {
        val startedAt = nowNanos()
        val storage = PlatformDatabaseData(StorageData.FileBacked(standardizedPath(sharedRoot)))
        val keychain = ApplePersistenceConfig(serviceName = keychainServiceName)
        return FeasibilityProbeResult(
            gate = "apple-persistence-security",
            passed = false,
            elapsedNanos = nowNanos() - startedAt,
            detail = "linked storage=${storage.storageData::class.simpleName}; " +
                    "keychainService=${keychain.serviceName}; accessGroup=UNCONFIGURABLE; " +
                    "accessibility=UNCONFIGURABLE; native SQLDelight database encryption=NOT_IMPLEMENTED"
        )
    }

    private suspend fun openThenCloseCoreCrypto(coreCryptoRoot: String) {
        // Disposable deterministic key for an isolated probe database only. Never use this in production.
        val passphrase = ByteArray(CORE_CRYPTO_KEY_SIZE) { index -> (index + 1).toByte() }
        val central = coreCryptoCentral(coreCryptoRoot, passphrase)
        central.proteusClient().close()
    }
}

public data class FeasibilityProbeResult(
    public val gate: String,
    public val passed: Boolean,
    public val elapsedNanos: Long,
    public val detail: String
)

public data class FeasibilityLockAttempt(
    public val acquired: Boolean,
    public val elapsedNanos: Long,
    public val detail: String,
    public val lock: FeasibilityProcessLock?
)

public class FeasibilityProcessLock internal constructor(
    private val lease: ProcessLockLease
) {
    public fun release() {
        lease.release()
    }
}

private inline fun measure(gate: String, block: () -> String): FeasibilityProbeResult {
    val startedAt = nowNanos()
    return runCatching { block() }.fold(
        onSuccess = { detail ->
            FeasibilityProbeResult(gate, true, nowNanos() - startedAt, detail)
        },
        onFailure = { failure ->
            FeasibilityProbeResult(gate, false, nowNanos() - startedAt, failure.message ?: failure.toString())
        }
    )
}

private suspend inline fun measureSuspending(
    gate: String,
    crossinline block: suspend () -> String
): FeasibilityProbeResult {
    val startedAt = nowNanos()
    return runCatching { block() }.fold(
        onSuccess = { detail ->
            FeasibilityProbeResult(gate, true, nowNanos() - startedAt, detail)
        },
        onFailure = { failure ->
            FeasibilityProbeResult(gate, false, nowNanos() - startedAt, failure.message ?: failure.toString())
        }
    )
}

private fun accountRoot(sharedRoot: String, accountDigest: String): String {
    require(sharedRoot.startsWith('/')) { "Shared root must be an absolute caller-supplied path" }
    require(accountDigest.matches(Regex("[A-Za-z0-9_-]+"))) { "Account digest contains unsafe path characters" }
    return "${standardizedPath(sharedRoot)}/kalium-nse-feasibility/v1/$accountDigest"
}

private fun standardizedPath(path: String): String =
    NSURL.fileURLWithPath(path, isDirectory = true).URLByStandardizingPath?.path
        ?: error("Could not standardize path")

private fun requireDirectory(path: String) {
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    ) { "Could not create directory: $path" }
}

private fun writeFile(path: String, content: ByteArray) {
    val descriptor = open(path, O_RDWR or O_CREAT, S_IRUSR or S_IWUSR)
    check(descriptor >= 0) { "open($path) failed: ${lastPosixError()}" }
    try {
        check(ftruncate(descriptor, 0) == 0) { "truncate($path) failed: ${lastPosixError()}" }
        check(lseek(descriptor, 0, 0) >= 0) { "seek($path) failed: ${lastPosixError()}" }
        val written = content.usePinned { pinned ->
            write(descriptor, pinned.addressOf(0), content.size.toULong())
        }
        check(written == content.size.toLong()) { "write($path) wrote $written/${content.size} bytes" }
    } finally {
        close(descriptor)
    }
}

private fun readFile(path: String, expectedSize: Int): ByteArray {
    val descriptor = open(path, O_RDONLY)
    check(descriptor >= 0) { "open($path) failed: ${lastPosixError()}" }
    return try {
        val result = ByteArray(expectedSize)
        val readCount = result.usePinned { pinned ->
            read(descriptor, pinned.addressOf(0), expectedSize.toULong())
        }
        check(readCount == expectedSize.toLong()) { "read($path) read $readCount/$expectedSize bytes" }
        result
    } finally {
        close(descriptor)
    }
}

private fun nowNanos(): Long = memScoped {
    val time = alloc<timespec>()
    check(clock_gettime(CLOCK_MONOTONIC.toUInt(), time.ptr) == 0) { "clock_gettime failed: ${lastPosixError()}" }
    time.tv_sec * NANOS_PER_SECOND + time.tv_nsec
}

private fun lastPosixError(): String = strerror(errno)?.toKString() ?: "errno=$errno"

private const val PROBE_ACCOUNT = "probe-account"
private const val PROBE_CLIENT = "probe-client"
private const val PROBE_ACCOUNT_CLIENT_DIGEST =
    "9c03173842651044f0848cfb08e7ef905916c4eae2d198cb7ab691d9124ee5ba"
private const val RECEIVING_LINKAGE_CONTENT_BYTE: Byte = 3
private const val CORE_CRYPTO_KEY_SIZE = 32
private const val NANOS_PER_SECOND = 1_000_000_000L
