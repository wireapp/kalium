package com.wire.kalium.cryptography

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

actual open class BaseProteusClientTest actual constructor() {
    actual fun createProteusClient(userId: UserId): ProteusClient {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/proteus", isDirectory = true)
        return ProteusClient(rootDir.absoluteString!!, userId.value)
    }

}
