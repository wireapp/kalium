/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ServerError
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.exceptions.KaliumException
import okio.ByteString.Companion.encodeUtf8

/** One receiving attempt. Holds only code-defined labels and a short hash of the current event ID. */
public class ReceivingDiagnostics {
    internal var stage: String = "OPEN_ACCOUNT"
    internal var eventType: String? = null
        private set
    internal var eventHash: String? = null
        private set
    internal var isTarget: Boolean? = null
        private set
    internal var errorCode: String? = null
        private set
    internal var failureType: String? = null
        private set
    internal var httpStatus: Int? = null
        private set
    internal var networkError: String? = null
        private set

    internal fun fetching() {
        stage = "FETCH"
        clearEvent()
    }

    internal fun beginEvent(target: Boolean, eventId: String) {
        stage = "READ_EVENT"
        clearEvent()
        isTarget = target
        eventHash = eventId.encodeUtf8().sha256().hex().take(EVENT_HASH_LENGTH)
    }

    internal fun decoding(content: EventContentDTO) {
        stage = "DECODE"
        // Unknown.type is server supplied. Kotlin class names for known DTOs are code defined.
        eventType = if (content is EventContentDTO.Unknown) "UNKNOWN_EVENT" else content::class.simpleName ?: "OTHER_EVENT"
    }

    internal fun unsupportedEvent() {
        errorCode = "UNSUPPORTED_EVENT"
    }

    internal fun failed(failure: Throwable) {
        val types = mutableListOf<String>()
        val seen = mutableListOf<Throwable>()
        var current: Throwable? = failure
        while (current != null && seen.size < MAX_FAILURE_DEPTH && seen.none { it === current }) {
            val item = current
            seen += item
            types += safeFailureType(item)
            val core = (item as? KaliumSyncException)?.coreFailureCause
            val response = (core as? NetworkFailure.ServerMiscommunication)?.reason?.errorResponse() ?: errorResponse(item)
            if (response != null) {
                httpStatus = response.code
                networkError = safeNetworkError(response.label)
            }
            if (core != null) {
                val category = safeCoreFailureType(core)
                types += category
                if (errorCode == null) errorCode = category
            }
            current = when (core) {
                is NetworkFailure.ServerMiscommunication -> core.rootCause
                is MLSFailure.Generic -> core.rootCause
                is CoreFailure.Unknown -> core.rootCause
                is StorageFailure.Generic -> core.rootCause
                else -> item.cause
            }
        }
        failureType = types.joinToString("/")
        if (errorCode == null) errorCode = "UNEXPECTED_EXCEPTION"
    }

    /** Uses the app's configured Kalium writer and level; no extra sink or retained log. */
    public fun record(status: String, reason: String?, shouldNotify: Boolean) {
        val logger = kaliumLogger
        if (logger.logLevel == KaliumLogLevel.DISABLED) return
        val failed = status == "FAILED"
        val level = if (failed) KaliumLogLevel.ERROR else KaliumLogLevel.INFO
        if (logger.logLevel.ordinal > level.ordinal) return
        // A failing host writer must not change the receiving result.
        runCatching {
            logger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER).logStructuredJson(
                level,
                if (failed) "Failure handling NSE receiving" else "Success handling NSE receiving",
                mapOf(
                    "outcome" to if (failed) "failure" else "success",
                    "reason" to safeResultReason(reason),
                    "stage" to stage,
                    "event" to eventType,
                    "position" to isTarget?.let { if (it) "TARGET" else "EARLIER" },
                    "eventHash" to eventHash,
                    "error" to errorCode,
                    "failureType" to failureType,
                    "httpStatus" to httpStatus,
                    "networkError" to networkError,
                    "shouldNotify" to shouldNotify,
                )
            )
        }
    }

    private fun clearEvent() {
        eventType = null
        eventHash = null
        isTarget = null
        errorCode = null
        failureType = null
        httpStatus = null
        networkError = null
    }
}

private const val EVENT_HASH_LENGTH = 24
private const val MAX_FAILURE_DEPTH = 8

private fun errorResponse(failure: Throwable): ServerError.Response? = when (failure) {
    is KaliumException.InvalidRequestError -> failure.errorResponse
    is KaliumException.ServerError -> failure.errorResponse
    is KaliumException.RedirectError -> failure.errorResponse
    else -> null
}?.let { ServerError.Response(it.code, it.message, it.label) }

private fun ServerError.errorResponse(): ServerError.Response? = when (this) {
    is ServerError.InvalidRequest -> errorResponse
    is ServerError.Server -> errorResponse
    is ServerError.Redirect -> errorResponse
    else -> null
}

private fun safeFailureType(failure: Throwable): String = when (failure) {
    is KaliumSyncException -> "KaliumSyncException"
    is KaliumException.InvalidRequestError -> "InvalidRequestError"
    is KaliumException.ServerError -> "ServerError"
    is KaliumException.RedirectError -> "RedirectError"
    is kotlinx.coroutines.CancellationException -> "CancellationException"
    is IllegalStateException -> "IllegalStateException"
    is IllegalArgumentException -> "IllegalArgumentException"
    else -> "OtherException"
}

private fun safeCoreFailureType(failure: CoreFailure): String = when (failure) {
    is MLSFailure -> failure::class.simpleName ?: "OtherMLSFailure"
    is NetworkFailure.ServerMiscommunication -> "ServerMiscommunication"
    is CoreFailure.Unknown -> "UnknownFailure"
    is StorageFailure.Generic -> "StorageGeneric"
    else -> "OtherCoreFailure"
}

private fun safeResultReason(reason: String?): String? = when (reason) {
    "INVALID_REQUEST", "TIMEOUT", "NOT_PREPARED", "MISSING_STORAGE", "PROTECTED_STORAGE",
    "STORAGE", "INCOMPATIBLE", "BUSY" -> reason
    null -> null
    else -> "OTHER"
}

/** Only fixed categories are emitted, never arbitrary server labels or messages. */
internal fun safeNetworkError(label: String): String = when (label) {
    "no-conversation" -> "NO_CONVERSATION"
    "access-denied" -> "ACCESS_DENIED"
    "operation-denied" -> "OPERATION_DENIED"
    "not-a-member" -> "NOT_A_MEMBER"
    "not-a-conversation-member" -> "NOT_A_CONVERSATION_MEMBER"
    "no-conversation-member" -> "NO_CONVERSATION_MEMBER"
    "action-denied" -> "ACTION_DENIED"
    "missing-auth" -> "MISSING_AUTH"
    "invalid-credentials" -> "INVALID_CREDENTIALS"
    "not-connected" -> "NOT_CONNECTED"
    "no-team-member" -> "NOT_TEAM_MEMBER"
    "not-found" -> "NOT_FOUND"
    else -> "OTHER"
}
