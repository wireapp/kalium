package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.location.Location
import com.wire.kalium.cryptography.PreKeyCrypto

data class RegisterClientParam(
    val password: String,
    val preKeys: List<PreKeyCrypto>,
    val lastKey: PreKeyCrypto,
    val capabilities: List<ClientCapability>?
)

data class DeleteClientParam(
    val password: String,
    val clientId: ClientId /* = com.wire.kalium.logic.data.id.PlainId */
)

data class Client(
    val clientId: ClientId,
    val type: ClientType,
    val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    val location: Location?,
    val deviceType: DeviceType?,
    val label: String?,
    val cookie: String?,
    val capabilities: Capabilities?,
    val model: String?
)

data class Capabilities(
    val capabilities: List<ClientCapability>
)

enum class ClientType {
    Temporary,
    Permanent,
    LegalHold;
}

enum class DeviceType {
    Phone,
    Tablet,
    Desktop,
    LegalHold;
}

enum class ClientCapability {
    LegalHoldImplicitConsent;
}
