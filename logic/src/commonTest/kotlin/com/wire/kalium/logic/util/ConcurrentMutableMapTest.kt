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
package com.wire.kalium.logic.util

import co.touchlab.stately.collections.ConcurrentMutableMap
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentMutableMapTest {

    @Test
    fun givenConcurrentMap_whenSafeComputeIfAbsentIsCalledWith_thenTheSecondIsIgnored() {
        val map = ConcurrentMutableMap<String, String>()
        map.safeComputeIfAbsent("a") { "c" }
        map.safeComputeIfAbsent("a") { "d" }
        assertEquals("c", map["a"])
    }
}
