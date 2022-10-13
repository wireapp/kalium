package com.wire.kalium.calling.types

import com.sun.jna.IntegerType
import com.sun.jna.Native

class Size_t(val value: Long = 0) : IntegerType(Native.SIZE_T_SIZE, value, true) {
    override fun toByte(): Byte = value.toByte()

    override fun toChar(): Char = value.toInt().toChar()

    override fun toShort(): Short = value.toShort()
}
