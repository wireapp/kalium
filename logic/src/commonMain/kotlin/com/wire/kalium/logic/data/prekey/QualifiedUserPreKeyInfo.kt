package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.user.UserId

data class QualifiedUserPreKeyInfo(val userId: UserId, val clientsInfo: List<ClientPreKeyInfo>)

data class ClientPreKeyInfo(val clientId: String, val preKey: PreKeyCrypto)
