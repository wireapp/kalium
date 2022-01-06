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

external open class Encoder {
    open var buffer: Any
    open var view: Any
    open fun get_buffer(): ArrayBuffer
    open var _new_buffer_length: Any
    open var _grow_buffer: Any
    open var _ensure: Any
    open var _advance: Any
    open var _write: Any
    open var _write_type_and_len: Any
    open var _u8: Any
    open var _u16: Any
    open var _u32: Any
    open var _u64: Any
    open var _f32: Any
    open var _f64: Any
    open var _bytes: Any
    open fun u8(value: Number): Encoder
    open fun u16(value: Number): Encoder
    open fun u32(value: Number): Encoder
    open fun u64(value: Number): Encoder
    open fun i8(value: Number): Encoder
    open fun i16(value: Number): Encoder
    open fun i32(value: Number): Encoder
    open fun i64(value: Number): Encoder
    open fun f32(value: Number): Encoder
    open fun f64(value: Number): Encoder
    open fun bool(value: Boolean): Encoder
    open fun bytes(value: ArrayBuffer): Encoder
    open fun bytes(value: Uint8Array): Encoder
    open fun text(value: String): Encoder
    open fun `null`(): Encoder
    open fun undefined(): Encoder
    open fun array(len: Number): Encoder
    open fun array_begin(): Encoder
    open fun array_end(): Encoder
    open fun `object`(len: Number): Encoder
    open fun object_begin(): Encoder
    open fun object_end(): Encoder
}
