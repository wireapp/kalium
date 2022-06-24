package com.wire.kalium.cryptography

/** Read the given resource as binary data. */
actual fun readBinaryResource(
    resourceName: String
): ByteArray {
    return ClassLoader
        .getSystemResourceAsStream(resourceName)!!
        .readBytes()
}
