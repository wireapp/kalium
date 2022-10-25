package com.wire.kalium.calling.types

import com.sun.jna.IntegerType

typealias Handle = Uint32_t

private const val integerSize = 4

data class Uint32_t(val value: Long = 0) : IntegerType(integerSize, value, true) {
    override fun toByte(): Byte = value.toByte()

    override fun toChar(): Char = value.toInt().toChar()

    override fun toShort(): Short = value.toShort()
}
