package com.wire.kalium.util.string

import kotlin.test.Test

class UTF16BEByteArrayTest {

    @Test
    fun test() {
        val test = "testString"
        val test1 = test.toUTF16BEByteArray()
        println(test1)

    }

}
