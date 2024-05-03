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

external open class PublicKey(pubEdward: Uint8Array, pubCurve: Uint8Array) {
    open var pub_edward: Uint8Array
    open var pub_curve: Uint8Array
    open fun verify(signature: Uint8Array, message: Uint8Array): Boolean
    open fun verify(signature: Uint8Array, message: String): Boolean
    open fun fingerprint(): String

    companion object {
        var propertiesLength: Any
        fun encode(encoder: Encoder, publicKey: PublicKey): Encoder
        fun decode(decoder: Decoder): PublicKey
    }
}
