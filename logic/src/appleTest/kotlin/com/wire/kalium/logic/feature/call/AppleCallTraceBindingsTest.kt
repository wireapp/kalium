/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.logger.AppleCallTrace
import com.wire.kalium.logger.CallTraceEvent
import com.wire.kalium.logger.CallTraceRole
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleCallTraceBindingsTest {
    @Test
    fun staleReleasePreservesReplacementAndRawIdentityNeverLeavesBinding() {
        val lines = mutableListOf<String>()
        val writer = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) { lines += message }
        }
        AppleCallTrace.configure(KaliumLogger.Config(KaliumLogLevel.INFO, listOf(writer)), CallTraceRole.APP)
        val account = UserId("private-account", "secret.example")
        val conversation = ConversationId("private-conversation", "secret.example")
        val first = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val second = "aaaaaaaa-bbbb-cccc-dddd-ffffffffffff"
        val engine = "11111111-2222-3333-4444-555555555555"
        val old = AppleCallTraceBindings.bind(account, conversation, first)
        val current = AppleCallTraceBindings.bind(account, conversation, second)
        try {
            AppleCallTraceBindings.release(old)
            AppleCallTrace.record(CallTraceEvent.AVS_ESTABLISHED, AppleCallTraceBindings.context(account, conversation, engine))
            assertTrue(lines.last().contains("attempt=$second"))
            assertFalse(lines.any { "private-" in it || "secret.example" in it })
            AppleCallTraceBindings.release(current)
            AppleCallTrace.record(CallTraceEvent.AVS_CLOSED, AppleCallTraceBindings.context(account, conversation, engine))
            assertFalse(lines.last().contains("attempt="))
        } finally {
            AppleCallTraceBindings.release(current)
            AppleCallTrace.configure(KaliumLogger.Config.DISABLED, CallTraceRole.APP)
        }
    }
}
