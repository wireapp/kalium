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

package com.wire.kalium.logic.feature

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class CoreLogicReleaseSessionScopeTest {

    @Test
    fun givenOpenSessionScope_whenReleasing_thenCryptoIsClosedAndScopeReopens() = runTest {
        val root = Files.createTempDirectory("release-session").toFile()
        val userId = UserId("user", "example.com")
        storeTestAccount(root, userId)
        val logic = coreLogic(root.absolutePath)
        try {
            val released = logic.getSessionScope(userId)
            released.userStorage.database.metadataDAO.insertValue(VALUE, KEY)
            val releasedProteus = released.proteusClientProvider.getOrCreate()
            val fingerprint = releasedProteus.localFingerprint()

            // Real time: the scope's IO work finishes on real dispatchers, not on the test clock.
            withContext(Dispatchers.Default) { logic.releaseSessionScope(userId) }

            assertNull(logic.userSessionScopeProvider.value.get(userId))
            assertFails { releasedProteus.localFingerprint() }
            val reopened = logic.getSessionScope(userId)
            assertNotSame(released, reopened)
            assertEquals(fingerprint, reopened.proteusClientProvider.getOrCreate().localFingerprint())
            assertEquals(VALUE, reopened.userStorage.database.metadataDAO.valueByKey(KEY))
        } finally {
            withContext(Dispatchers.Default) { logic.releaseSessionScope(userId) }
            root.deleteRecursively()
        }
    }

    @Test
    fun givenAccountWorkDoesNotStop_whenReleasing_thenFailureIsRethrownAndScopeIsForgotten() = runTest {
        val root = Files.createTempDirectory("release-session-stuck").toFile()
        val userId = UserId("user", "example.com")
        storeTestAccount(root, userId)
        val logic = coreLogic(root.absolutePath)
        val scope = logic.getSessionScope(userId)
        val release = CompletableDeferred<Unit>()
        val stuckWorker = scope.launch { withContext(NonCancellable) { release.await() } }
        try {
            assertFailsWith<TimeoutCancellationException> { logic.releaseSessionScope(userId) }
            assertNull(logic.userSessionScopeProvider.value.get(userId))
        } finally {
            release.complete(Unit)
            stuckWorker.join()
            scope.userStorage.database.close()
            root.deleteRecursively()
        }
    }

    private suspend fun ProteusClient.localFingerprint(): String = transaction("fingerprint") { it.getLocalFingerprint() }

    private fun coreLogic(rootPath: String) = CoreLogic(
        rootPath = rootPath,
        kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
        userAgent = "kalium-release-test",
    )

    private companion object {
        const val KEY = "release-test-key"
        const val VALUE = "release-test-value"
    }
}
