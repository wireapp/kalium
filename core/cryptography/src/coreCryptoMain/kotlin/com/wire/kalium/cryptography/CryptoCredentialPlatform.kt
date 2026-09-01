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
package com.wire.kalium.cryptography

import com.wire.crypto.Credential
import com.wire.crypto.CredentialRef
import com.wire.kalium.cryptography.utils.toCryptography

internal class CryptoCredentialImpl(
    credential: Credential
) : CryptoCredential {
    private var native: Credential? = credential

    override fun exportPem(): String = borrow().exportPem()

    override fun close() {
        native?.close()
        native = null
    }

    internal fun borrow(): Credential = checkNotNull(native) {
        "CryptoCredential has already been closed"
    }
}

internal class CryptoCredentialRefImpl(
    credentialRef: CredentialRef
) : CryptoCredentialRef {
    private var native: CredentialRef? = credentialRef
    private var ownsNative = true

    override fun credentialType(): CredentialType = borrow().type().toCryptography()

    override fun publicKeyHash(): ByteArray = borrow().publicKeyHash()

    override fun close() {
        if (ownsNative) native?.close()
        native = null
        ownsNative = false
    }

    internal fun borrow(): CredentialRef = checkNotNull(native) {
        "CryptoCredentialRef has already been closed"
    }

    internal fun transferOwnership() {
        ownsNative = false
    }
}

internal fun CryptoCredential.unwrap(): Credential =
    requireNotNull(this as? CryptoCredentialImpl) {
        "Unsupported CryptoCredential implementation"
    }.borrow()

internal fun CryptoCredentialRef.unwrap(): CredentialRef =
    requireNotNull(this as? CryptoCredentialRefImpl) {
        "Unsupported CryptoCredentialRef implementation"
    }.borrow()

internal fun CryptoCredentialRef.transferNativeOwnership() {
    requireNotNull(this as? CryptoCredentialRefImpl) {
        "Unsupported CryptoCredentialRef implementation"
    }.transferOwnership()
}
