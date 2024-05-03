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

package com.wire.kalium.persistence.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LRUCacheTest {

    @Test
    fun givenAnElementIsCached_whenGettingTheSecondTime_shouldExecuteTheProviderLambdaOnlyTheFirstTime() = runTest {
        val cache = createCache(100)
        val itemKey = 42
        var providerExecCount = 0

        cache.get(itemKey) {
            providerExecCount++
            "Number one"
        }
        assertEquals(1, providerExecCount)

        cache.get(itemKey) { error("This should not be executed as the item is already cached") }
        assertEquals(1, providerExecCount)
    }

    @Test
    fun givenAKeyAndProvider_whenInvokingTheProvider_shouldPassTheKeyAsParameter() = runTest {
        val cache = createCache(100)
        val itemKey = 42
        var providerExecCount = 0

        cache.get(itemKey) {
            assertEquals(itemKey, it)
            providerExecCount++
            "Number one"
        }
        assertEquals(1, providerExecCount)
    }

    @Test
    fun givenAnElementIsCached_whenGettingItsValue_shouldReturnTheProvidedValue() = runTest {
        val cache = createCache(100)
        val itemKey = 42
        val expectedElement = "I'm expected"

        val result = cache.get(itemKey) { expectedElement }
        assertEquals(expectedElement, result)
    }

    @Test
    fun givenAnElementIsRemoved_whenGettingItsValueTheSecondTime_shouldInvokeTheProviderLambda() = runTest {
        val cache = createCache(100)
        val itemKey = 42
        cache.get(itemKey) { "Zio" }

        cache.remove(itemKey)
        val secondResult = cache.get(itemKey) { "Expected Value" }

        val thirdResult = cache.get(itemKey) { "Zia" }

        assertEquals(secondResult, thirdResult)
    }

    @Test
    fun givenTheCacheCountExceedsTheLimit_whenGettingAnExpiredValue_shouldInvokeTheNewProvider() = runTest {
        val limit = 10
        val cache = createCache(limit)
        val itemsToExpire = 2
        val expectedElement = "The replacement"

        repeat(limit + itemsToExpire) { index ->
            cache.get(index) { "The originals" }
        }
        val result = cache.get(0) { expectedElement }

        assertEquals(expectedElement, result)
    }

    @Test
    fun givenTheCacheCountExceedsTheLimit_whenGettingARecentValue_shouldNotInvokeTheNewProvider() = runTest {
        val limit = 10
        val cache = createCache(limit)
        val itemsToExpire = 2
        val expectedElement = "The originals"

        repeat(limit + itemsToExpire) { index ->
            cache.get(index) { expectedElement }
        }
        val result = cache.get(itemsToExpire + 1) {
            error("Should not invoke this, as the item was not expired")
        }

        assertEquals(expectedElement, result)
    }

    private fun createCache(size: Int) = LRUCache<Int, String>(size)
}
