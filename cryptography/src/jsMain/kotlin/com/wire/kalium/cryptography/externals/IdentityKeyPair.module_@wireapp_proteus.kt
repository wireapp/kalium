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

external open class IdentityKeyPair(publicKey: IdentityKey = definedExternally, secretKey: SecretKey = definedExternally, version: Number = definedExternally) {
    open var public_key: IdentityKey
    open var secret_key: SecretKey
    open var version: Number
    open fun serialise(): ArrayBuffer

    companion object {
        var propertiesLength: Any
        fun deserialise(buf: ArrayBuffer): IdentityKeyPair
        fun encode(encoder: Encoder, identityKeyPair: IdentityKeyPair): Encoder
        fun decode(decoder: Decoder): IdentityKeyPair
    }
}
