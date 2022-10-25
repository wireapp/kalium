package com.wire.kalium.cryptography.backup

import com.wire.kalium.cryptography.CryptoUserID

data class BackupPassphrase(
    val password: String = "",
    val userId: CryptoUserID
)
