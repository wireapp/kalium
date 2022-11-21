package com.wire.kalium.util.string

expect fun String.toUTF16BEByteArray(): ByteArray

expect fun ByteArray.toStringFromUtf16BE(): String

expect fun ByteArray.toStringFromUtf8(): String

expect fun ByteArray.toHexString(): String

expect fun Long.toByteArray() : ByteArray
