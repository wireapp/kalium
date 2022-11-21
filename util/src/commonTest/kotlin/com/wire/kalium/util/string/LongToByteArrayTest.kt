package com.wire.kalium.util.string

import com.wire.kalium.util.long.toByteArray
import kotlin.test.Test

class LongToByteArrayTest {

    @Test
    fun test() {
        val result = 9_223_372_036_854_775_803.toByteArray()

        println(result)
    }

}
