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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CryptoStateChangeHookRegistryTest {

    @Test
    fun givenNoHookRegistered_whenStateChanges_thenNoError() = runTest {
        val registry = CryptoStateChangeHookRegistry()

        registry.onCryptoStateChanged(USER_ID)
    }

    @Test
    fun givenHookRegistered_whenStateChanges_thenHookInvoked() = runTest {
        val registry = CryptoStateChangeHookRegistry()
        val recordingHook = RecordingHookNotifier()

        registry.register(recordingHook)
        registry.onCryptoStateChanged(USER_ID)

        assertEquals(listOf(USER_ID), recordingHook.calls)
    }

    @Test
    fun givenHookRegistered_whenHookThrows_thenExceptionSwallowed() = runTest {
        val registry = CryptoStateChangeHookRegistry()

        registry.register(CryptoStateChangeHookNotifier { throw IllegalStateException("boom") })

        registry.onCryptoStateChanged(USER_ID)
    }

    @Test
    fun givenHookRegistered_whenHookThrowsCancellation_thenExceptionPropagated() = runTest {
        val registry = CryptoStateChangeHookRegistry()

        registry.register(CryptoStateChangeHookNotifier { throw CancellationException("cancel") })

        assertFailsWith<CancellationException> {
            registry.onCryptoStateChanged(USER_ID)
        }
    }

    @Test
    fun givenHookRegistered_whenUnregistered_thenHookNotInvoked() = runTest {
        val registry = CryptoStateChangeHookRegistry()
        val recordingHook = RecordingHookNotifier()

        registry.register(recordingHook)
        registry.unregister(recordingHook)
        registry.onCryptoStateChanged(USER_ID)

        assertEquals(emptyList(), recordingHook.calls)
    }

    private class RecordingHookNotifier : CryptoStateChangeHookNotifier {
        val calls = mutableListOf<UserId>()

        override suspend fun onCryptoStateChanged(userId: UserId) {
            calls += userId
        }
    }

    private companion object {
        val USER_ID = UserId("user", "domain")
    }
}
