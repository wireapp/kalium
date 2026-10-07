/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logger

import platform.Foundation.NSLock
import platform.Foundation.NSUUID

/** Closed diagnostic vocabulary. No signaling payloads or user identifiers belong in these records. */
public enum class CallTraceEvent {
    BIND, UNBIND, DURABLE_READY, NSE_INCOMING, NSE_CLOSED, NSE_MISSED,
    JOURNAL_CLOSED, NATIVE_ENQUEUE, AVS_INCOMING, AVS_ANSWERED, AVS_ESTABLISHED, AVS_CLOSED, CALLBACK_EFFECT,
    AVS_START, AVS_ANSWER, START, ANSWER, END, REJECT, MUTE, SIGNAL_SEND, ENGINE_CREATE, ENGINE_READY, ENGINE_RELEASE,
    RUNTIME_SETUP, RUNTIME_RUN,
}

public enum class CallTraceResult { REQUESTED, ACCEPTED, COMPLETED, FAILED, CANCELLED, STALE }
public enum class CallTraceRole { APP, NSE }

/** Opaque, random UUIDs only. Invalid input is omitted rather than interrupting the operation being diagnosed. */
public class CallTraceContext(
    attempt: String? = null,
    signal: String? = null,
    engine: String? = null,
    operation: String? = null,
) {
    internal val attempt = opaqueId(attempt)
    internal val signal = opaqueId(signal)
    internal val engine = opaqueId(engine)
    internal val operation = opaqueId(operation)

    public fun withSignal(signal: String?): CallTraceContext = CallTraceContext(attempt, signal, engine, operation)

    internal companion object {
        fun opaqueId(value: String?): String? = value?.takeIf { it.matches(UUID_PATTERN) }?.lowercase()
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

/** Reuses host-configured sinks. A sequence orders observations, not native engine causality. */
public object AppleCallTrace {
    private val process = CallTraceProcess()

    public fun configure(config: KaliumLogger.Config, role: CallTraceRole) {
        val logger = KaliumLogger(config, "CallTrace")
        process.configure(role) { logger.i(it) }
    }

    public fun record(
        event: CallTraceEvent,
        context: CallTraceContext = CallTraceContext(),
        result: CallTraceResult? = null,
        code: Int? = null,
        flag: Boolean? = null,
    ) = process.record(event, context, result, code, flag)
}

internal object AppleDiagnosticProcess { val id: String = NSUUID().UUIDString.lowercase() }

internal class CallTraceProcess {
    private val lock = NSLock()
    private val boot = AppleDiagnosticProcess.id
    private var sequence = 0L
    private var role = CallTraceRole.APP
    private var emit: ((String) -> Unit)? = null

    fun configure(role: CallTraceRole, emit: (String) -> Unit) = locked {
        this.role = role
        this.emit = emit
    }

    fun record(event: CallTraceEvent, context: CallTraceContext, result: CallTraceResult?, code: Int?, flag: Boolean?) {
        val record = locked {
            sequence++
            emit to buildString {
                append("CALL_TRACE v=1 boot=$boot role=${role.name} seq=$sequence event=${event.name}")
                context.attempt?.let { append(" attempt=$it") }
                context.signal?.let { append(" signal=$it") }
                context.engine?.let { append(" engine=$it") }
                context.operation?.let { append(" operation=$it") }
                result?.let { append(" result=${it.name}") }
                code?.let { append(" code=$it") }
                flag?.let { append(" flag=$it") }
            }
        }
        // Never call a writer under the trace lock, or let diagnostics change calling behavior.
        runCatching { record.first?.invoke(record.second) }
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }
}
