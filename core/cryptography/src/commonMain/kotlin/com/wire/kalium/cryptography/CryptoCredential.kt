/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

/**
 * A Core Crypto credential acquired independently of a client transaction.
 *
 * The underlying native type deliberately stays internal so higher layers cannot couple
 * themselves to the Core Crypto API. A credential becomes persistent only after it is
 * installed by Core Crypto.
 */
interface CryptoCredential {
    /** Export the public credential as PEM; X509 credentials return their full certificate chain. */
    fun exportPem(): String

    /** Release the native credential. Safe to call more than once. */
    fun close()
}

/** An owned reference to a credential stored by Core Crypto. */
interface CryptoCredentialRef {
    /** Credential kind stored behind this reference. */
    fun credentialType(): CredentialType

    /** Stable identifier used to recover this installed credential after a process restart. */
    fun publicKeyHash(): ByteArray

    /** Release the native reference. Safe to call more than once. */
    fun close()
}
