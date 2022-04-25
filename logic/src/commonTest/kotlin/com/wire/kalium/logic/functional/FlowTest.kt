package com.wire.kalium.logic.functional

import app.cash.turbine.test
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
