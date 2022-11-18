package com.wire.kalium.util.string

expect fun String.toUTF16BEByteArray(): ByteArray

expect fun ByteArray.toStringFromUtf16BE(): String
