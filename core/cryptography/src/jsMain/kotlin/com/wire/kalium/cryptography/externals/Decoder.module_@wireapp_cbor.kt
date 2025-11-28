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

@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")

package com.wire.kalium.cryptography.externals

import kotlin.js.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.dom.parsing.*
import org.w3c.dom.svg.*
import org.w3c.dom.url.*
import org.w3c.fetch.*
import org.w3c.files.*
import org.w3c.notifications.*
import org.w3c.performance.*
import org.w3c.workers.*
import org.w3c.xhr.*

external interface DecoderConfig {
    var max_array_length: Number
    var max_bytes_length: Number
    var max_nesting: Number
    var max_object_size: Number
    var max_text_length: Number
}

external open class Decoder(buffer: ArrayBuffer, config: DecoderConfig = definedExternally) {
    open var buffer: Any
    open var config: Any
    open var view: Any
    open var _advance: Any
    open var _read: Any
    open var _u8: Any
    open var _u16: Any
    open var _u32: Any
    open var _u64: Any
    open var _f32: Any
    open var _f64: Any
    open var _read_length: Any
    open var _bytes: Any
    open var _read_type_info: Any
    open var _type_info_with_assert: Any
    open var _read_unsigned: Any
    open var _read_signed: Any
    open var _skip_until_break: Any
    open var _skip_value: Any
    open fun u8(): Number
    open fun u16(): Number
    open fun u32(): Number
    open fun u64(): Number
    open fun i8(): Number
    open fun i16(): Number
    open fun i32(): Number
    open fun i64(): Number
    open fun unsigned(): Number
    open fun int(): Number
    open fun f16(): Number
    open fun f32(): Number
    open fun f64(): Number
    open fun bool(): Boolean
    open fun bytes(): ArrayBuffer
    open fun text(): String
    open fun <T> optional(closure: () -> T): T?
    open fun array(): Number
    open fun `object`(): Number
    open fun skip(): Boolean

    companion object {
        var _check_overflow: Any
    }
}
