package com.wire.kalium.util.string

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.get

// TODO: JS has not the priority as for now, but we were playing around with JS
// and the result as for now is the code below, it is not working correctly as for now
actual fun String.toUTF16BEByteArray(): ByteArray {
    val str = this

    val buf = ArrayBuffer(str.length * 2)
    val bufView = Uint16Array(buf)

//     js("var buf = new ArrayBuffer(str.length*2);")
//     js("var bufView = new Uint16Array (buf);")
    js("for (var i= 0, strLen = str.length; i < strLen; i++) { bufView[i] = str.charCodeAt(i); }")

    val test = ByteArray(bufView.length)

    for (i in 0..bufView.length) {
        test[i] = bufView[i].toByte()
    }

    return test
}

actual fun ByteArray.toStringFromUtf16BE(): String {
    TODO("Not yet implemented")
}
