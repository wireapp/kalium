package com.wire.kalium.util.string

actual fun String.toUTF16BEByteArray(): ByteArray {
    val str = this

    js("var buf = new ArrayBuffer(str.length*2);")
    js("var bufView = new Uint16Array (buf);")
    js("for (var i= 0, strLen = str.length; i < strLen; i++) { bufView[i] = str.charCodeAt(i); }")
    return js("bufView;") as ByteArray
}
