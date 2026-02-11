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
package com.wire.kalium.logic.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MutexProviderTest {

    @Test
    fun givenTwoSameKeys_whenWithLockIsCalled_thenOnlyOneExecutionOccursAtATime() = runTest {
        val mutexProvider = MutexProvider<String>()
        var secondActionExecuted = false
        var onWaitingToUnlockCalled = false
        launch {
            mutexProvider.withLock("key1") {
                delay(1000) // second job is started in the middle of this delay
                assertEquals(false, secondActionExecuted) // second job action should not be executed before first one releases the lock
            }
            delay(1000) // ensure second job has time to execute after job1 releases the lock
            assertEquals(true, secondActionExecuted) // second job action should be executed after first one releases the lock
            assertEquals(true, onWaitingToUnlockCalled) // it should be called as second job was waiting to unlock
        }
        launch {
            mutexProvider.withLock("key1", { onWaitingToUnlockCalled = true }) {
                secondActionExecuted = true
            }
        }
    }

    @Test
    fun givenTwoDifferentKeys_whenWithLockIsCalled_thenBothExecutionsOccurIndependently() = runTest {
        val mutexProvider = MutexProvider<String>()
        var secondActionExecuted = false
        var onWaitingToUnlockCalled = false
        launch {
            mutexProvider.withLock("key1") {
                delay(1000) // second job is started in the middle of this delay
                assertEquals(true, secondActionExecuted) // second job action should already be executed independently
                assertEquals(false, onWaitingToUnlockCalled) // it should not be called as second job was not waiting to unlock
            }
        }
        launch {
            mutexProvider.withLock("key2", { onWaitingToUnlockCalled = true }) {
                secondActionExecuted = true
            }
        }
    }

    @Test
    fun givenActionThrowsException_whenWithLockIsCalled_thenFinallyDecreaseMutex() = runTest {
        val mutexProvider = MutexProvider<String>()
        val exception = IllegalStateException("Test exception")

        assertFailsWith<IllegalStateException> {
            mutexProvider.withLock("key1") {
                throw exception
            }
        }

        assertEquals(false, mutexProvider.doesLockCurrentlyExist("key1")) // lock should be removed after exception is thrown

    }
}
