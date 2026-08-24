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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.cryptography

import com.wire.crypto.Credential
import com.wire.crypto.CredentialRef

internal class CryptoCredentialImpl(
    credential: Credential
) : CryptoCredential {
    private var nativeCredential: Credential? = credential
    private val exportedPem: String

    init {
        try {
            exportedPem = credential.exportPem()
        } catch (throwable: Throwable) {
            credential.close()
            nativeCredential = null
            throw throwable
        }
    }

    override fun exportPem(): String = exportedPem

    internal fun borrow(): Credential = checkNotNull(nativeCredential) {
        "CryptoCredential has already been closed"
    }

    override fun close() {
        nativeCredential?.close()
        nativeCredential = null
    }
}

internal class CryptoCredentialRefImpl(
    credentialRef: CredentialRef,
    private var ownsCredentialRef: Boolean = true
) : CryptoCredentialRef {
    private var nativeCredentialRef: CredentialRef? = credentialRef

    internal fun borrow(): CredentialRef = checkNotNull(nativeCredentialRef) {
        "CryptoCredentialRef has already been closed"
    }

    internal fun takeOwnership(): CredentialRef {
        check(ownsCredentialRef) { "CryptoCredentialRef does not own its native reference" }
        ownsCredentialRef = false
        return borrow()
    }

    internal fun restoreOwnership(credentialRef: CredentialRef) {
        check(nativeCredentialRef === credentialRef) { "Cannot restore a different native credential reference" }
        check(!ownsCredentialRef) { "CryptoCredentialRef already owns its native reference" }
        ownsCredentialRef = true
    }

    override fun publicKeyHash(): ByteArray = borrow().publicKeyHash()

    override fun close() {
        if (!ownsCredentialRef) return
        nativeCredentialRef?.close()
        nativeCredentialRef = null
        ownsCredentialRef = false
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

internal fun CryptoCredentialRef.takeNativeOwnership(): CredentialRef =
    requireNotNull(this as? CryptoCredentialRefImpl) {
        "Unsupported CryptoCredentialRef implementation"
    }.takeOwnership()

internal fun CryptoCredentialRef.restoreNativeOwnership(credentialRef: CredentialRef) {
    requireNotNull(this as? CryptoCredentialRefImpl) {
        "Unsupported CryptoCredentialRef implementation"
    }.restoreOwnership(credentialRef)
}
