package com.wire.kalium.util.string

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class UTF16BEByteArrayTest {

    @Test
    fun test() {
        val test = "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!"

        val dupa = test.toUTF16BEByteArray()

        val hex = dupa.toStringFromUtf16BE()
        val hex1 = dupa.toHexString()
        println(dupa)

    }


}

fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
