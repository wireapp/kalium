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

package com.wire.kalium.network.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggablePathSegmentsTest {
    @Test
    fun givenNetworkPathConstants_whenCheckingLoggableSegments_thenEverySegmentIsAllowlisted() {
        val constantValues = File("src/commonMain/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> pathConstant.findAll(file.readText()).map { it.groupValues[1] } }
            .toList()
        assertTrue(constantValues.size > MINIMUM_EXPECTED_CONSTANTS, "Found only ${constantValues.size} path constants")

        val missing = constantValues
            .flatMap { it.trim('/').split('/') }
            .filter { it.isNotEmpty() && !versionSegment.matches(it) && it !in loggablePathSegments }
            .toSortedSet()

        assertEquals(emptySet(), missing, "Add these static segments to loggablePathSegments in ObfuscateUtil.kt")
    }

    private companion object {
        const val MINIMUM_EXPECTED_CONSTANTS = 50
        val pathConstant = Regex("""const val (?:\w*PATH\w*|PUSH_TOKEN|FEATURE_CONFIG)\s*=\s*"([^"$]*)"""")
        val versionSegment = Regex("v[0-9]+")
    }
}
