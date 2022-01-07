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

external interface SerialisedJSON {
    var id: Number
    var key: String
}

external open class PreKeyBundle {
    open var identity_key: IdentityKey
    open var prekey_id: Number
    open var public_key: PublicKey
    open var signature: Uint8Array?
    open var version: Number
    constructor(publicIdentityKey: IdentityKey, preKey: PreKey)
    constructor(publicIdentityKey: IdentityKey, preKeyId: Number, publicKey: PublicKey, signature: Uint8Array? = definedExternally, version: Number = definedExternally)
    constructor(publicIdentityKey: IdentityKey, preKeyId: Number, publicKey: PublicKey)
    constructor(publicIdentityKey: IdentityKey, preKeyId: Number, publicKey: PublicKey, signature: Uint8Array? = definedExternally)
    open fun serialise(): ArrayBuffer
    open fun serialised_json(): SerialisedJSON

    companion object {
        var propertiesLength: Any
        fun deserialise(buf: ArrayBuffer): PreKeyBundle
        fun encode(encoder: Encoder, preKeyBundle: PreKeyBundle): Encoder
        fun decode(decoder: Decoder): PreKeyBundle
    }
}
