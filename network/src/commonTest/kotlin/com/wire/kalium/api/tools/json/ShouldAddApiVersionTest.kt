package com.wire.kalium.api.tools.json

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
