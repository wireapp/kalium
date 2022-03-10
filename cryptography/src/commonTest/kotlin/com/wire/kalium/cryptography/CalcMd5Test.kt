package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.calcMd5
import kotlin.test.Test
import kotlin.test.assertEquals

class CalcMd5Test {

    @Test
    @IgnoreJS
    fun testGivenByteArray_whenCallingCalcMd5_returnsExpectedDigest() {
        val input = "Hello World".encodeToByteArray()
        val digest = calcMd5(input)

        assertEquals("sQqNsWTgdUEFt6mb5y4/5Q==", digest)
    }

}
