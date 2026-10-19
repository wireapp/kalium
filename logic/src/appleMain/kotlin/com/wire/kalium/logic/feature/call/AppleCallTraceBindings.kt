/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logger.AppleCallTrace
import com.wire.kalium.logger.CallTraceContext
import com.wire.kalium.logger.CallTraceEvent
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import platform.Foundation.NSLock

/** Raw account/conversation keys stay in memory and never enter the diagnostic record. */
public object AppleCallTraceBindings {
    private val lock = NSLock()
    private val bindings = mutableMapOf<Pair<UserId, ConversationId>, AppleCallTraceBinding>()

    public fun bind(account: UserId, conversation: ConversationId, attemptId: String): AppleCallTraceBinding {
        val binding = locked {
            val key = account to conversation
            bindings[key]?.takeIf { it.attemptId == attemptId } ?: AppleCallTraceBinding(account, conversation, attemptId).also {
                bindings[key] = it
            }
        }
        AppleCallTrace.record(CallTraceEvent.BIND, CallTraceContext(attempt = attemptId))
        return binding
    }

    public fun release(binding: AppleCallTraceBinding) {
        val released = locked {
            val key = binding.account to binding.conversation
            if (bindings[key] === binding) {
                bindings.remove(key)
                true
            } else false
        }
        if (released) AppleCallTrace.record(CallTraceEvent.UNBIND, CallTraceContext(attempt = binding.attemptId))
    }

    /** Capture once at owner or command entry; delayed work must not resolve a replacement attempt. */
    public fun context(
        account: UserId,
        conversation: ConversationId,
        engine: String? = null,
        operation: String? = null,
    ): CallTraceContext = locked {
        CallTraceContext(attempt = bindings[account to conversation]?.attemptId, engine = engine, operation = operation)
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }
}

/** Release this exact token after cleanup; an old token cannot remove a replacement binding. */
public class AppleCallTraceBinding internal constructor(
    internal val account: UserId,
    internal val conversation: ConversationId,
    internal val attemptId: String,
)
