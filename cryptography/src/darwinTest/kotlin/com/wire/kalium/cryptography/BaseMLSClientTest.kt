package com.wire.kalium.cryptography

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.URLByAppendingPathComponent

actual open class BaseMLSClientTest actual constructor() {
    actual fun createMLSClient(clientId: CryptoQualifiedClientId): MLSClient {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/mls", isDirectory = true)
        NSFileManager.defaultManager.createDirectoryAtURL(rootDir, true, null, null)
        val keyStore = rootDir.URLByAppendingPathComponent("keystore-$clientId")!!
        return MLSClientImpl(keyStore.path!!, MlsDBSecret("test"), clientId)
    }
}
