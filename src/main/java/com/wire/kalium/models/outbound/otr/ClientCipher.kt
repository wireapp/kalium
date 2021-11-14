package com.wire.kalium.models.outbound.otr

import kotlinx.serialization.Serializable

// <ClientId, Cipher> // cipher is base64 encoded
@Serializable
class ClientCipher : HashMap<String, String>() {
    override fun get(key: String): String? {
        return super.get(key)
    }
}
