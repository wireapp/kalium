package com.wire.kalium.cryptography.exceptions

class CryptographyException(override val message: String, val rootCause: Throwable? = null) : Exception(message)
