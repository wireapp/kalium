/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.calling

import kotlin.test.Test
import kotlin.test.assertTrue

class AppleAvsSftResponseTest {
    @Test
    fun givenFailedSftRequest_whenRespondingWithoutData_thenDoesNotThrow() {
        assertTrue(AppleAvsFlowManager().startIfAvailable())

        // Failed SFT requests answer with no data; AVS ignores the unknown handle.
        AppleAvs.bridge.respondToSft(handle = 0u, error = 1, data = byteArrayOf(), context = null)
    }
}
