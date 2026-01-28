/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset

import kotlin.test.assertEquals

class AudioNormalizedLoudnessBuilderMock(private val defaultResult: ByteArray? = null) : AudioNormalizedLoudnessBuilder {
    private val results = mutableMapOf<String, Pair<ByteArray?, Int>>()

    fun every(filePath: String, result: ByteArray?) { results[filePath] = result to 0 }

    fun assertInvoked(filePath: String, expected: Int = 1) = results.getOrPut(filePath) { null to 0 }.second.let { actual ->
        assertEquals(
            expected = expected,
            actual = actual,
            message = "AudioNormalizedLoudnessBuilderMock was not invoked the expected number of times.\n\n" +
                    "Expected $expected invocations of $filePath\nActual: $actual\n"
        )
    }

    override suspend fun invoke(filePath: String): ByteArray? = (results[filePath]?.first ?: defaultResult).also { result ->
        results[filePath] = result to (results[filePath]?.second ?: 0) + 1 // increment call count
    }
}
