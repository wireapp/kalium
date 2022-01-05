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

external open class KeyPair {
    open var public_key: PublicKey
    open var secret_key: SecretKey
    constructor()
    constructor(publicKey: PublicKey, secretKey: SecretKey)

    companion object {
        var propertiesLength: Any
        fun construct_private_key(ed25519_key_pair: KeyPair): SecretKey
        fun construct_public_key(ed25519_key_pair: KeyPair): PublicKey
        fun encode(encoder: Encoder, keyPair: KeyPair): Encoder
        fun decode(decoder: Decoder): KeyPair
    }
}
