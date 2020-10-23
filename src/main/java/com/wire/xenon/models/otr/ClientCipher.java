package com.wire.xenon.models.otr;

import java.util.HashMap;

// <ClientId, Cipher> // cipher is base64 encoded
class ClientCipher extends HashMap<String, String> {

    public String get(String clientId) {
        return super.get(clientId);
    }
}
