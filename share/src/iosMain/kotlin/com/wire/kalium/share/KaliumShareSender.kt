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

package com.wire.kalium.share

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.configuration.MessageContentPreference
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.sync.receiving.CatchUpOutcome
import com.wire.kalium.logic.sync.receiving.NotificationProcessor
import com.wire.kalium.logic.sync.receiving.UnnotifiedMessageAlerts
import com.wire.kalium.logic.sync.receiving.ReceivedMessageResult
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import com.wire.kalium.logic.sync.receiving.ReceivingOptions
import com.wire.kalium.logic.sync.receiving.cryptoTransactions
import com.wire.kalium.logic.sync.receiving.holdLive
import com.wire.kalium.logic.sync.receiving.notificationProcessor
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.KeychainAccessibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Shared configuration must exactly match app preparation, including the canonical App Group root. */
public data class KaliumShareConfiguration(
    public val rootPath: String,
    public val keychainService: String,
    public val keychainAccessGroup: String?,
    public val keychainAccessibility: KaliumShareKeychainAccessibility,
    public val userAgent: String,
    public val certificatePins: Map<String, List<String>>,
    /** Must match the app's KaliumConfigs build switches; events received while sending are processed with them. */
    public val guestRoomLink: Boolean = true,
    public val selfDeletingMessages: Boolean = true,
    public val drivePermissionsEnabled: Boolean = false,
    public val linkPreviewEnabled: Boolean = false,
    /** Null allows all file types; a list must match the app's BuildFileRestrictionState.AllowSome. */
    public val allowedFileExtensions: List<String>? = null,
)

public enum class KaliumShareKeychainAccessibility {
    WHEN_UNLOCKED,
    AFTER_FIRST_UNLOCK,
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
    WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    WHEN_PASSCODE_SET_THIS_DEVICE_ONLY,
}

/** A conversation the extension can send to, most recently active first. */
public data class KaliumShareConversation(
    public val conversationValue: String,
    public val conversationDomain: String,
    public val name: String?,
    public val isGroup: Boolean,
    /** Whether the current destination supports files. */
    public val acceptsFiles: Boolean,
)

/** [failureReason] is set, and the list empty, when the account could not be opened or its team enforces App Lock. */
public data class KaliumShareConversations(
    public val conversations: List<KaliumShareConversation>,
    /** The account's own name, to tell accounts apart. */
    public val selfName: String? = null,
    public val failureReason: String? = null,
    /** What the account may share as files; null when its team disabled file sharing or the setting cannot be read. */
    public val files: KaliumShareFiles? = null,
)

/** The account's file rules, as the app applies them. [send] checks them again. */
public data class KaliumShareFiles(
    /** Null allows every file type; otherwise the file name's extension must be listed, matching case. */
    public val allowedExtensions: List<String>?,
    public val fileSizeLimitBytes: Long,
    /** Images Wire displays (JPEG, PNG, GIF, WebP) have their own limit, whatever the team. */
    public val imageSizeLimitBytes: Long,
) {
    /** `FILE_TYPE_RESTRICTED` or `FILE_TOO_LARGE` when the account refuses the file, or null. */
    public fun refusal(name: String, mimeType: String, sizeInBytes: Long): String? =
        SharePolicy(allowedExtensions, fileSizeLimitBytes).refusal(name, mimeType, sizeInBytes)?.name
}

/**
 * A file to share, prepared by the host. Kalium reads [path] while [KaliumShareSender.send] runs and never moves
 * or deletes it. Dimensions and duration are 0 when unknown.
 */
public data class KaliumShareAttachment(
    public val path: String,
    /** Sent as the file's name; its extension decides file type restrictions. */
    public val name: String,
    public val mimeType: String,
    /** Images and videos. */
    public val width: Int = 0,
    public val height: Int = 0,
    /** Videos and audio. */
    public val durationMs: Long = 0,
)

/** Upload progress over the plain size of all attachments; called from a background thread. */
public fun interface KaliumShareProgress {
    public fun uploaded(bytes: Long, totalBytes: Long)
}

/** [text] may be blank when there are attachments; each attachment is sent as its own message, then the text. */
public data class KaliumShareRequest(
    public val userValue: String,
    public val userDomain: String,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val text: String,
    public val attachments: List<KaliumShareAttachment>,
    /** Additional host message-preview restriction for alerts; the saved account choice is read for each request. */
    public val showMessageContent: Boolean,
    /** Bounds the live hold that sends; uploads run before it, with a budget of their own that grows with the files' size. */
    public val budgetMilliseconds: Long,
)

public enum class KaliumShareStatus {
    /** The backend accepted the message. */
    SENT,

    /** The message is stored as failed; the user can retry it in Wire. */
    FAILED,

    /**
     * The account could not reach the backend, or its WebSocket did not go live, so nothing was sent.
     * A stored message is failed, as for [FAILED].
     */
    OFFLINE,

    /** Sending needs the app: consent, verification, MLS work, recovery or an account in use. */
    OPEN_APP,
}

public data class KaliumShareResult(
    public val status: KaliumShareStatus,
    /**
     * A stored message, when one was created: the first one not sent, or else the last one sent. Messages after
     * one that was not sent are kept in Wire as failed too.
     */
    public val messageId: String? = null,
    /** Diagnostic code only; never contains account data or message content. */
    public val failureReason: String? = null,
    /**
     * Unnotified message alerts, oldest first. The backend sent no push for events delivered over the
     * open socket, so the host posts each; Kalium has already marked them notified.
     */
    public val alerts: List<KaliumShareAlert> = emptyList(),
    /** Events may have arrived that could not be processed in time; the host shows a generic alert. */
    public val presentGenericAlert: Boolean = false,
)

/** Text and names are present only when preview is allowed. */
public data class KaliumShareAlert(
    public val messageId: String,
    public val conversationValue: String,
    public val conversationDomain: String,
    public val text: String?,
    public val conversationName: String?,
    public val senderName: String?,
    public val isGroupConversation: Boolean,
)

/**
 * Sends shared text and files from the share extension through the app's sender (ADR 12). It uploads files
 * first, then holds the account and keeps it live over the WebSocket while it sends, then processes what
 * arrived and returns unnotified alerts.
 */
public class KaliumShareSender(private val configuration: KaliumShareConfiguration) {

    /** Completes once another process wants the account; see [yieldAccount]. */
    private val accountWanted = Job()

    /**
     * Another process needs the account (ADR 12): a [send] that has not stored a message yet stops uploading or
     * catching up, releases the account and returns [KaliumShareStatus.OPEN_APP] with `ACCOUNT_WANTED`, for the host
     * to hand the share to the app; its alerts are still owed. A send that stores messages finishes within its budget.
     * Later sends return `ACCOUNT_WANTED` without taking the account.
     */
    public fun yieldAccount() {
        accountWanted.complete()
    }

    /** `BUSY` as [KaliumShareConversations.failureReason] can mean the app owns the account: ask it instead. */
    @Throws(CancellationException::class)
    public suspend fun conversations(userValue: String, userDomain: String): KaliumShareConversations {
        // Listing reads only the database, so it holds the account without opening CoreCrypto or the network.
        val userId = UserId(userValue, userDomain)
        val result = AppleAccountRuntime.useDatabase(configuration.rootPath, userId, persistence) { database, prepared ->
            database.sharePicker(prepared.userId, options().fileRestrictionState)
        }
        return when (result) {
            is Either.Left -> KaliumShareConversations(emptyList(), failureReason = result.value.name)
            is Either.Right -> result.value.toKalium()
        }
    }

    /**
     * Uploads the attachments, reporting [progress], then sends within [KaliumShareRequest.budgetMilliseconds].
     * Uploads end when they finish, fail or run out of their budget, or when the host cancels or calls [yieldAccount];
     * nothing is stored before every upload succeeded.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount") // Keep the request and account guards before taking the account.
    @Throws(CancellationException::class)
    public suspend fun send(request: KaliumShareRequest, progress: KaliumShareProgress?): KaliumShareResult {
        val files = request.attachments.map { it.toShareFile() }
        val invalid = when {
            request.budgetMilliseconds !in 1..MAX_BUDGET_MS || request.attachments.size > MAX_ATTACHMENTS -> "INVALID_REQUEST"
            request.text.isBlank() && request.attachments.isEmpty() -> "INVALID_REQUEST"
            null in files -> "FILE_UNREADABLE"
            else -> null
        }
        if (invalid != null) return failure(invalid)
        // The account is another process's now; taking it again would keep that process waiting.
        if (accountWanted.isCompleted) return KaliumShareResult(KaliumShareStatus.OPEN_APP, failureReason = ACCOUNT_WANTED)
        // A result produced before the budget ends survives a slow runtime close: its message alerts are marked notified.
        var produced: KaliumShareResult? = null
        return try {
            val result = use(UserId(request.userValue, request.userDomain)) { runtime ->
                uploadAndShare(runtime, request, files.filterNotNull(), progress) { produced = it }
            }
            when (result) {
                is Either.Left -> KaliumShareResult(KaliumShareStatus.OPEN_APP, failureReason = result.value.name)
                is Either.Right -> result.value
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") cleanup: Exception) {
            // Sending finished and marked its message alerts notified; only closing the runtime failed.
            produced ?: throw cleanup
        }
    }

    /** Uploading needs no live socket, so it runs before the hold, with its own budget. */
    private suspend fun uploadAndShare(
        runtime: AppleAccountRuntime,
        request: KaliumShareRequest,
        files: List<ShareFile>,
        progress: KaliumShareProgress?,
        produced: (KaliumShareResult) -> Unit,
    ): KaliumShareResult {
        // The extension cannot unlock App Lock, so a team that enforces it keeps sharing in the app.
        if (runtime.database.appLockEnforcedByTeam(options().fileRestrictionState)) {
            return KaliumShareResult(KaliumShareStatus.OPEN_APP, failureReason = APP_LOCK)
        }
        val conversationId = ConversationId(request.conversationValue, request.conversationDomain)
        val uploaded = yieldingUploads(
            uploadBudget(files.sumOf { it.sizeInBytes }),
            accountWanted,
            discard = runtime::discardSharedDrafts
        ) {
            runtime.uploadSharedFiles(conversationId, files, options().fileRestrictionState) { bytes, total ->
                progress?.uploaded(bytes, total)
            }
        }
        return uploaded.fold({ KaliumShareResult(it.status.toKalium(), failureReason = it.reason) }) { uploads ->
            val budget = request.budgetMilliseconds.milliseconds
            val started = TimeSource.Monotonic.markNow()
            val sending = ShareSending(runtime, runtime.cryptoTransactions(), options().fileRestrictionState)
            // Messages stored before the deadline stay in Wire as failed, and the result says so.
            try {
                withTimeoutOrNull(budget) {
                    share(runtime, request, sending, uploads, budget, started).also(produced)
                } ?: failure("TIMEOUT").copy(presentGenericAlert = true, messageId = sending.firstStoredId)
            } finally {
                // Once stored, the message owns the drafts and the app can retry publication.
                if (sending.firstStoredId == null) runtime.discardSharedDrafts(uploads)
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun share(
        runtime: AppleAccountRuntime,
        request: KaliumShareRequest,
        sending: ShareSending,
        uploads: List<ShareItem>,
        budget: Duration,
        started: TimeSource.Monotonic.ValueTimeMark,
    ): KaliumShareResult {
        val processor = runtime.notificationProcessor(options())
        val held = runtime.holdLive(budget * CONNECT_SHARE) { hold ->
            when (hold) {
                // As the app's sender waits for live, the extension sends only over a live socket.
                CatchUpOutcome.LIVE -> catchUpAndSend(
                    runtime = runtime,
                    processor = processor,
                    sending = sending,
                    request = request,
                    // The text follows the files, as a caption does.
                    items = sharedItems(uploads, request.text),
                    shouldStopCatchingUp = { started.elapsedNow() > budget * SEND_BY_SHARE || accountWanted.isCompleted },
                    shouldStopSending = { started.elapsedNow() > budget * SENT_BY_SHARE },
                )
                // Recovering missed events needs slow sync, which only the app runs.
                CatchUpOutcome.NEEDS_SLOW_SYNC -> ShareOutcome(ShareStatus.OPEN_APP, hold.name) to emptyList()
                else -> ShareOutcome(ShareStatus.OFFLINE, "CATCH_UP_${hold.name}") to emptyList()
            }
        }
        // The backend sent no push for events delivered over the open socket, so present unnotified messages.
        val processed = processor.processToLive(null) {
            started.elapsedNow() > budget * PROCESS_SHARE || accountWanted.isCompleted
        }
        val (outcome, unnotifiedBeforeSending) = held.value
        val unnotified = unnotifiedBeforeSending + runtime.takeUnnotifiedAlerts(processed)
        if (unnotified.isNotEmpty()) {
            runtime.database.messageDAO.rememberNotificationMessages(
                unnotified.map { NotificationMessageKey(it.messageId, QualifiedIDEntity(it.conversationValue, it.conversationDomain)) }
            )
        }
        val preference = runtime.readMessageContentPreference()
        val alerts = unnotified.map { alert(it, request.showMessageContent, preference) }
        // Events may remain unpresented when the socket ended on its own after going live, or when processing
        // deferred or left them for the app. A hold that never went live sent nothing and asks for no alert.
        val unpresented = held.started == CatchUpOutcome.LIVE && held.closed != CatchUpOutcome.LIVE ||
            processed.stoppedReason != QUEUE_END || processed.appWorkPending != null ||
            runtime.database.eventDAO.observeUnprocessedEvents(limit = 1).first().isNotEmpty()
        return KaliumShareResult(
            status = outcome.status.toKalium(),
            messageId = outcome.messageId,
            failureReason = outcome.reason,
            alerts = alerts,
            presentGenericAlert = unpresented && alerts.isEmpty(),
        )
    }

    /** Returns the send's outcome and the unnotified alerts from before the outgoing messages were stored. */
    @Suppress("LongParameterList")
    private suspend fun catchUpAndSend(
        runtime: AppleAccountRuntime,
        processor: NotificationProcessor,
        sending: ShareSending,
        request: KaliumShareRequest,
        items: List<ShareItem>,
        shouldStopCatchingUp: () -> Boolean,
        shouldStopSending: () -> Boolean,
    ): Pair<ShareOutcome, List<ReceivedMessageResult>> {
        // A stale MLS epoch or client list fails the send, so bring the account current first.
        val processed = processor.processToLive(null, shouldStopCatchingUp)
        // Storing the outgoing message marks earlier messages notified, so take their alerts first.
        val unnotified = runtime.takeUnnotifiedAlerts(processed)
        val unfinished = processed.appWorkPending?.name ?: processed.stoppedReason.takeIf { it != QUEUE_END }
        val outcome = when {
            // Nothing is stored yet, so the app can take the share over.
            accountWanted.isCompleted -> ShareOutcome(ShareStatus.OPEN_APP, ACCOUNT_WANTED)
            // Unapplied events can carry consent or membership changes; sending would ignore them.
            unfinished != null -> ShareOutcome(ShareStatus.OPEN_APP, "PROCESSING_$unfinished")
            else -> sending.send(ConversationId(request.conversationValue, request.conversationDomain), items, shouldStopSending)
        }
        return outcome to unnotified
    }

    /** Stored alerts may be presented only after the queued prefix was processed without deferral or handoff. */
    private suspend fun AppleAccountRuntime.takeUnnotifiedAlerts(processed: ReceivedNotificationResult): List<ReceivedMessageResult> =
        if (!processed.prefixComplete) emptyList()
        else UnnotifiedMessageAlerts(database, prepared.userId).take()

    private suspend fun <T> use(userId: UserId, block: suspend (AppleAccountRuntime) -> T) = AppleAccountRuntime.use(
        configuration.rootPath,
        userId,
        persistence,
        configuration.userAgent,
        configuration.certificatePins,
        block = block,
    )

    private val persistence get() = ApplePersistenceConfig(
        serviceName = configuration.keychainService,
        keychainAccessibility = configuration.keychainAccessibility.toApple(),
        accessGroup = configuration.keychainAccessGroup,
    )

    private fun options() = ReceivingOptions(
        guestRoomLink = configuration.guestRoomLink,
        selfDeletingMessages = configuration.selfDeletingMessages,
        drivePermissionsEnabled = configuration.drivePermissionsEnabled,
        linkPreviewEnabled = configuration.linkPreviewEnabled,
        fileRestrictionState = configuration.allowedFileExtensions?.let {
            BuildFileRestrictionState.AllowSome(it)
        } ?: BuildFileRestrictionState.NoRestriction
    )

    internal fun alert(
        message: ReceivedMessageResult,
        showContent: Boolean,
        preference: Either<StorageFailure, MessageContentPreference>,
    ): KaliumShareAlert {
        val canPreview = showContent && preference.allowsPreview() && message.mayPreview
        return KaliumShareAlert(
            messageId = message.messageId,
            conversationValue = message.conversationValue,
            conversationDomain = message.conversationDomain,
            text = message.text?.takeIf { canPreview },
            conversationName = message.conversationName?.takeIf { canPreview },
            senderName = message.senderName?.takeIf { canPreview },
            isGroupConversation = canPreview && message.isGroupConversation,
        )
    }

    private fun Either<StorageFailure, MessageContentPreference>.allowsPreview() = when (this) {
        is Either.Left -> false
        is Either.Right -> when (value) {
            MessageContentPreference.Unset, MessageContentPreference.Enabled -> true
            MessageContentPreference.Disabled -> false
        }
    }

    private fun KaliumShareKeychainAccessibility.toApple(): KeychainAccessibility = when (this) {
        KaliumShareKeychainAccessibility.WHEN_UNLOCKED -> KeychainAccessibility.WhenUnlocked
        KaliumShareKeychainAccessibility.WHEN_UNLOCKED_THIS_DEVICE_ONLY -> KeychainAccessibility.WhenUnlockedThisDeviceOnly
        KaliumShareKeychainAccessibility.AFTER_FIRST_UNLOCK -> KeychainAccessibility.AfterFirstUnlock
        KaliumShareKeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> KeychainAccessibility.AfterFirstUnlockThisDeviceOnly
        KaliumShareKeychainAccessibility.WHEN_PASSCODE_SET_THIS_DEVICE_ONLY -> KeychainAccessibility.WhenPasscodeSetThisDeviceOnly
    }

    private fun failure(reason: String) = KaliumShareResult(KaliumShareStatus.FAILED, failureReason = reason)

    public companion object {
        /**
         * Closing an account failed in this process, so it keeps that account's lease until it exits: neither the
         * app nor the notification extension can open the account. End the process once the request ended.
         */
        public val hasQuarantinedAccount: Boolean get() = AppleAccountRuntime.hasQuarantinedRuntime

        private const val MAX_BUDGET_MS = 60_000L

        private const val QUEUE_END = "QUEUE_END"

        // Soft deadlines: connect, catch up, send, then close and process what arrived.
        private const val CONNECT_SHARE = 0.25
        private const val SEND_BY_SHARE = 0.45
        private const val SENT_BY_SHARE = 0.7
        private const val PROCESS_SHARE = 0.85
    }
}

/** The app sends at most this many files at once. */
internal const val MAX_ATTACHMENTS = 20

private const val APP_LOCK = "APP_LOCK"

/** Null when the file cannot be read. */
internal fun KaliumShareAttachment.toShareFile(): ShareFile? {
    val size = FileSystem.SYSTEM.metadataOrNull(path.toPath())?.takeIf { it.isRegularFile }?.size ?: return null
    return ShareFile(
        path = path,
        name = name,
        mimeType = mimeType,
        sizeInBytes = size,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        durationMs = durationMs.takeIf { it > 0 },
    )
}

internal fun SharePicker.toKalium() = if (appLock) {
    KaliumShareConversations(emptyList(), selfName, APP_LOCK)
} else {
    KaliumShareConversations(
        conversations = conversations.map {
            KaliumShareConversation(it.value, it.domain, it.name, it.isGroup, acceptsFiles = it.acceptsFiles)
        },
        selfName = selfName,
        files = files?.let { KaliumShareFiles(it.allowedExtensions, it.fileSizeLimitBytes, it.imageSizeLimitBytes) },
    )
}

private fun ShareStatus.toKalium() = when (this) {
    ShareStatus.SENT -> KaliumShareStatus.SENT
    ShareStatus.FAILED -> KaliumShareStatus.FAILED
    ShareStatus.OFFLINE -> KaliumShareStatus.OFFLINE
    ShareStatus.OPEN_APP -> KaliumShareStatus.OPEN_APP
}
