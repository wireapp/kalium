package com.wire.kalium.util.string

import kotlin.test.Test

class UTF16BEByteArrayTest {

    @Test
    fun test() {
        val test = "testString"
        val test1 = test.toUTF16BEByteArray()
        println("test prtinging ")

        println("test prtinging ")
        println("test prtinging ")
        println("test prtinging ")
        println("test prtinging ")
        println("test prtinging ")
        println("test prtinging ")
        println("test prtinging ")
//

        val test2 = test1.asUByteArray.joinToString("") { it.toString(16).padStart(2, '0') }
        throw RuntimeException("${}")
    }

}
