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

package com.wire.kalium.cryptography

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoreCryptoTransactionCancellationTest : BaseProteusClientTest() {

    private val alice = CryptoUserID("aliceId", "aliceDomain")
    private val bob = CryptoUserID("bobId", "bobDomain")
    private val aliceSessionId = CryptoSessionId(alice, CryptoClientId("aliceClient"))
    private val secret = ProteusDBSecret(ByteArray(32) { 0 })

    @Test
    fun cancellingTheCallerKeepsATransactionThatAlreadyStarted() = runTest {
        withContext(Dispatchers.Default) {
            val aliceClient = createProteusClient(createProteusStoreRef(alice), secret)
            val bobStore = createProteusStoreRef(bob)
            val bobClient = createProteusClient(bobStore, secret)
            val aliceKey = aliceClient.newPreKeysAuto(1).first()
            val started = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            var blockFinished = false

            val caller = launch {
                bobClient.transaction("cancelled-caller") { context ->
                    started.complete(Unit)
                    // A message send waits here for its POST, which may already have reached the backend.
                    withContext(NonCancellable) { proceed.await() }
                    context.createSession(aliceKey, aliceSessionId)
                    blockFinished = true
                }
            }
            withTimeout(10.seconds) { started.await() }
            caller.cancel()
            // The caller must not resume before the transaction it started has ended.
            delay(500.milliseconds)
            assertFalse(caller.isCompleted)
            proceed.complete(Unit)
            withTimeout(10.seconds) { caller.join() }

            assertTrue(blockFinished)
            // Rolling back would lose state the backend may already have seen. This throws SESSION_NOT_FOUND then.
            bobClient.close()
            val reopened = createProteusClient(bobStore, secret)
            reopened.transaction("check-session") { it.remoteFingerPrint(aliceSessionId) }
        }
    }
}
