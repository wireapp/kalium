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
