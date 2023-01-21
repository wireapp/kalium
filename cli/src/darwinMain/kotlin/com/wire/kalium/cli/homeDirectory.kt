package com.wire.kalium.cli

import platform.Foundation.NSHomeDirectory

actual fun homeDirectory(): String {
    return NSHomeDirectory()
}
