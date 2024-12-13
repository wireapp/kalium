/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.backup.envelope.header

import com.wire.backup.envelope.header.HashData.Companion.HASHED_USER_ID_SIZE_IN_BYTES
import com.wire.backup.envelope.header.HashData.Companion.MINIMUM_MEMORY_LIMIT
import com.wire.backup.envelope.header.HashData.Companion.SALT_SIZE_IN_BYTES
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class HashDataTest {

    @Test
    fun givenPassingParameters_thenShouldNotThrowException() {
        HashData(
            hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES) { 1U },
            salt = UByteArray(SALT_SIZE_IN_BYTES) { 2U },
            operationsLimit = 8U,
            hashingMemoryLimit = MINIMUM_MEMORY_LIMIT
        )
    }

    @Test
    fun givenShortHashedUserId_thenShouldThrowException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HashData(
                hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES - 1) { 1U },
                salt = UByteArray(SALT_SIZE_IN_BYTES) { 2U },
                operationsLimit = 8U,
                hashingMemoryLimit = MINIMUM_MEMORY_LIMIT
            )
        }
        assertContains(exception.message!!, "user ID")
    }

    @Test
    fun givenLongHashedUserId_thenShouldThrowException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HashData(
                hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES + 1) { 1U },
                salt = UByteArray(SALT_SIZE_IN_BYTES) { 2U },
                operationsLimit = 8U,
                hashingMemoryLimit = MINIMUM_MEMORY_LIMIT
            )
        }
        assertContains(exception.message!!, "user ID")
    }

    @Test
    fun givenShortSalt_thenShouldThrowException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HashData(
                hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES) { 1U },
                salt = UByteArray(SALT_SIZE_IN_BYTES - 1) { 2U },
                operationsLimit = 8U,
                hashingMemoryLimit = MINIMUM_MEMORY_LIMIT
            )
        }
        assertContains(exception.message!!, "Salt")
    }

    @Test
    fun givenLongSalt_thenShouldThrowException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HashData(
                hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES) { 1U },
                salt = UByteArray(SALT_SIZE_IN_BYTES + 1) { 2U },
                operationsLimit = 8U,
                hashingMemoryLimit = MINIMUM_MEMORY_LIMIT
            )
        }
        assertContains(exception.message!!, "Salt")
    }


    @Test
    fun givenMemoryLimitIsTooLow_thenShouldThrowException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HashData(
                hashedUserId = UByteArray(HASHED_USER_ID_SIZE_IN_BYTES) { 1U },
                salt = UByteArray(SALT_SIZE_IN_BYTES) { 2U },
                operationsLimit = 8U,
                hashingMemoryLimit = MINIMUM_MEMORY_LIMIT - 1
            )
        }
        assertContains(exception.message!!, "Memory Limit")
    }
}
