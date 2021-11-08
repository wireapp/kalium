package com.wire.kalium.models.otr

// <ClientId, Cipher> // cipher is base64 encoded
internal class ClientCipher : HashMap<String?, String?>() {
    fun get(clientId: String?): String? {
        return super.get(clientId)
    }
}
