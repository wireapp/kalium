package com.wire.kalium.util.string

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.get

actual fun String.toUTF16BEByteArray(): ByteArray {
    val str = this

    var buf = ArrayBuffer(str.length * 2)
    var bufView = Uint16Array(buf)

//     js("var buf = new ArrayBuffer(str.length*2);")
//     js("var bufView = new Uint16Array (buf);")
    js("for (var i= 0, strLen = str.length; i < strLen; i++) { bufView[i] = str.charCodeAt(i); }")

    val test = ByteArray(bufView.length)

    for (i in 0..bufView.length) {
        test[i] = bufView[i].toByte()
    }

    return test
}
