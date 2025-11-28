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

@file:JsModule("@wireapp/cryptobox")
@file:JsNonModule
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

external enum class TOPIC {
    NEW_PREKEYS /* = "new-prekeys" */,
    NEW_SESSION /* = "new-session" */
}

external interface `T$0` {
    var id: Number
    var key: String
}

external open class Cryptobox(engine: CRUDEngineBaseCollection, minimumAmountOfPreKeys: Number = definedExternally)  {
    open fun on(event: TOPIC, listener: (prekeys: Array<PreKey>) -> Unit): Cryptobox /* this */
    open fun on(event: TOPIC, listener: (session: String) -> Unit): Cryptobox /* this */
    open var cachedSessions: Any
    open var queues: Any
    open var minimumAmountOfPreKeys: Any
    open var store: Any
    open var identity: IdentityKeyPair
    open var lastResortPreKey: PreKey?
    open var get_session_queue: Any
    open var save_session_in_cache: Any
    open var load_session_from_cache: Any
    open var remove_session_from_cache: Any
    open fun create(): Promise<Array<PreKey>>
    open fun getIdentity(): IdentityKeyPair
    open fun load(): Promise<Array<PreKey>>
    open var init: Any
    open fun get_serialized_last_resort_prekey(): Promise<`T$0`>
    open var get_prekey: Any
    open fun get_prekey_bundle(preKeyId: Number = definedExternally): Promise<PreKeyBundle>
    open fun get_serialized_standard_prekeys(): Promise<Array<`T$0`>>
    open var publish_event: Any
    open var publish_prekeys: Any
    open var publish_session_id: Any
    open var refill_prekeys: Any
    open var create_new_identity: Any
    open var save_identity: Any
    open fun session_from_prekey(sessionId: String, preKeyBundle: ArrayBuffer): Promise<CryptoboxSession>
    open var session_from_message: Any
    open fun session_load(sessionId: String): Promise<CryptoboxSession>
    open var session_save: Any
    open var session_update: Any
    open fun session_delete(sessionId: String): Promise<String>
    open var create_last_resort_prekey: Any
    open fun serialize_prekey(prekey: PreKey): `T$0`
    open fun new_prekeys(start: Number, size: Number): Promise<Array<PreKey>>
    open fun encrypt(sessionId: String, payload: String, preKeyBundle: ArrayBuffer = definedExternally): Promise<ArrayBuffer>
    open fun encrypt(sessionId: String, payload: String): Promise<ArrayBuffer>
    open fun encrypt(sessionId: String, payload: Uint8Array, preKeyBundle: ArrayBuffer = definedExternally): Promise<ArrayBuffer>
    open fun encrypt(sessionId: String, payload: Uint8Array): Promise<ArrayBuffer>
    open fun decrypt(sessionId: String, ciphertext: ArrayBuffer): Promise<Uint8Array>
    open var deleteData: Any
    open var importIdentity: Any
    open var importPreKeys: Any
    open var importSessions: Any

    companion object {
        var VERSION: String
        var TOPIC: Any
    }
}
