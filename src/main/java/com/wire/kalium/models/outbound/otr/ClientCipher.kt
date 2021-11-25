package com.wire.kalium.models.outbound.otr

import kotlinx.serialization.Serializable

// <ClientId, Cipher> // cipher is base64 encoded
//@Serializable
typealias ClientCipher = HashMap<String, String>
