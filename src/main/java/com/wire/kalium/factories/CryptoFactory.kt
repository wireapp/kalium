package com.wire.kalium.factories

import kotlin.Throws
import java.util.UUID
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.crypto.Crypto

interface CryptoFactory {
    @Throws(CryptoException::class)
    open fun create(botId: UUID?): Crypto?
}
