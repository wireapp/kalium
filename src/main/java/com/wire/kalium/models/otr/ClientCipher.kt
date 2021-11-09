package com.wire.kalium.models.otr

// <ClientId, Cipher> // cipher is base64 encoded
class ClientCipher : HashMap<String, String>() {
    override fun get(key: String): String? {
        return super.get(key)
    }
}
