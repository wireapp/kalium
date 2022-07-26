package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.location.Location

data class RegisterClientParam(
    val password: String?,
    val preKeys: List<PreKeyCrypto>,
    val lastKey: PreKeyCrypto,
    val deviceType: DeviceType?,
    //val type: ClientType,
    val label: String?,
    val capabilities: List<ClientCapability>?,
    val model: String?
)

data class DeleteClientParam (
    val password: String?,
    val clientId: ClientId
)

data class Client(
    val id: ClientId,
    val type: ClientType,
    val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    val location: Location?,
    val deviceType: DeviceType?,
    val label: String?,
    val cookie: String?,
    val capabilities: Capabilities?,
    val model: String?
) {
    val name by lazy { model ?: label ?: "Unknown Client" } // TODO: ask design about the name when model/liable is null
}

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
    LegalHold,
    Unknown;
}

enum class ClientCapability {
    LegalHoldImplicitConsent;
}
