package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.id.QualifiedID

data class QualifiedUserPreKeyInfo(val userId: QualifiedID, val clientsInfo: List<ClientPreKeyInfo>)

data class ClientPreKeyInfo(val clientId: String, val preKey: PreKey)
