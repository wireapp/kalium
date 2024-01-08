/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
