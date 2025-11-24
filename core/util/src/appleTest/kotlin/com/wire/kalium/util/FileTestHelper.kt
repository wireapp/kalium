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
package com.wire.kalium.util

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

actual object FileTestHelper {
    actual fun createRandomDirectory(): String {
        val directory = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/testDirectory", isDirectory = true)
        NSFileManager.defaultManager.createDirectoryAtURL(directory, true, null, null)
        return directory.absoluteString!!
    }

    actual fun createRandomFileAt(path: String) {
        val directory = NSURL.fileURLWithPath(path + "/testFile", isDirectory = true)
        NSFileManager.defaultManager.createDirectoryAtURL(directory, true, null, null)
    }

    actual fun directoryExists(path: String): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }
}
