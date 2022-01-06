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
