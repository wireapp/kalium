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

external open class SecretKey(secEdward: Uint8Array, secCurve: Uint8Array) {
    open var sec_curve: Uint8Array
    open var sec_edward: Uint8Array
    open fun sign(message: Uint8Array): Uint8Array
    open fun sign(message: String): Uint8Array

    companion object {
        var propertiesLength: Any
        fun shared_secret(publicKey: PublicKey, secretKey: SecretKey): Uint8Array
        fun encode(encoder: Encoder, secretKey: SecretKey): Encoder
        fun decode(decoder: Decoder): SecretKey
    }
}
