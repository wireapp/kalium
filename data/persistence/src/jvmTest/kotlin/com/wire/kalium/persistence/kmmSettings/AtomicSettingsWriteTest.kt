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

package com.wire.kalium.persistence.kmmSettings

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtomicSettingsWriteTest {

    private val directory = Files.createTempDirectory("atomic-write").toFile()
    private val file = File(directory, "app-preference")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun givenFileHeldOpenForAMoment_whenWritten_thenTheMoveIsRetriedAndSucceeds() {
        var failuresLeft = 2
        writeAtomically(file, "new".encodeToByteArray()) { source, target ->
            if (failuresLeft-- > 0) throw AccessDeniedException(target.toString())
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

        assertEquals("new", file.readText())
    }

    @Test
    fun givenFileThatStaysLocked_whenWritten_thenTheLastFailureIsRethrown() {
        var attempts = 0

        assertFailsWith<AccessDeniedException> {
            retryWhileLocked(pause = {}) {
                attempts++
                throw AccessDeniedException(file.path)
            }
        }
        assertEquals(5, attempts)
    }

    @Test
    fun givenOtherFailure_whenWritten_thenItIsNotRetried() {
        var attempts = 0

        assertFailsWith<IllegalStateException> {
            retryWhileLocked(pause = {}) {
                attempts++
                error("not a locked file")
            }
        }
        assertEquals(1, attempts)
    }
}
