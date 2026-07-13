/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.messaging.receiving

import com.wire.kalium.cryptography.ProteusCoreCryptoContext

/**
 * Applies an already-received Proteus message to CoreCrypto state and exposes its plaintext.
 *
 * The handler deliberately runs inside [ProteusCoreCryptoContext.decryptMessage]. This preserves
 * the existing transaction and rollback semantics while allowing the full app or NSE to choose
 * how the decrypted bytes are materialized.
 */
public interface ProteusMessageDecryptor {
    public suspend fun <Result : Any> decrypt(
        context: ProteusCoreCryptoContext,
        message: ProteusEncryptedMessage,
        handleDecryptedMessage: suspend (ByteArray) -> Result
    ): Result
}

public class ProteusMessageDecryptorImpl : ProteusMessageDecryptor {
    override suspend fun <Result : Any> decrypt(
        context: ProteusCoreCryptoContext,
        message: ProteusEncryptedMessage,
        handleDecryptedMessage: suspend (ByteArray) -> Result
    ): Result = context.decryptMessage(
        sessionId = message.sessionId,
        message = message.encryptedMessage,
        handleDecryptedMessage = handleDecryptedMessage
    )
}
