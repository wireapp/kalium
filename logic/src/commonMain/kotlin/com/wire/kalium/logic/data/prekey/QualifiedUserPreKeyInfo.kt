package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.cryptography.PreKeyCrypto

data class QualifiedUserPreKeyInfo(val userId: QualifiedID, val clientsInfo: List<ClientPreKeyInfo>)

data class ClientPreKeyInfo(val clientId: String, val preKey: PreKeyCrypto)
