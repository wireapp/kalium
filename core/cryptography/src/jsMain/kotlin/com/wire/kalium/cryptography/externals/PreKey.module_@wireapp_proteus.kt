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

@file:JsModule("@wireapp/proteus")
@file:JsNonModule
@file:JsQualifier("keys")
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

external open class PreKey(keyId: Number = definedExternally, keyPair: KeyPair = definedExternally, version: Number = definedExternally) {
    open var key_id: Number
    open var key_pair: KeyPair
    open var version: Number
    open fun serialise(): ArrayBuffer

    companion object {
        var MAX_PREKEY_ID: Any = definedExternally
        var propertiesLength: Any
        fun last_resort(): PreKey
        fun generate_prekeys(start: Number, size: Number): Array<PreKey>
        fun deserialise(buf: ArrayBuffer): PreKey
        fun encode(encoder: Encoder, preKey: PreKey): Encoder
        fun decode(decoder: Decoder): PreKey
    }
}
