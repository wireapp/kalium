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

package com.wire.kalium.logic.functional

import app.cash.turbine.test
import com.wire.kalium.common.functional.flatMapFromIterable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTest {


    @Test
    fun givenAListOfItems_whenFlatMappingFromIterable_thenTheMappedResultsShouldBeEmitted() = runTest {
        val items = listOf(1, 2)

        val result = items.flatMapFromIterable { flowOf(it * 2) }

        result.test {
            assertEquals(listOf(2, 4), awaitItem())
            awaitComplete()
        }
    }
}
