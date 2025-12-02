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

package com.wire.kalium.api.common

import com.wire.kalium.network.shouldAddApiVersion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldAddApiVersionTest {

    @Test
    fun givenApiVersionIs0_enraptureFalse() {
        val apiVersion = 0
        shouldAddApiVersion(apiVersion).also { actual ->
            assertFalse(actual)
        }
    }

    @Test
    fun givenApiVersionEqualOrGraterThan1_thenReturnFalse() {
        val apiVersion = Random.nextInt(1, 100)
        shouldAddApiVersion(apiVersion).also { actual ->
            assertTrue(actual)
        }
    }
}
