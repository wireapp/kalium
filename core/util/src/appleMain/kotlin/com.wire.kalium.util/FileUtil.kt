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

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSError
import platform.Foundation.NSFileManager

actual object FileUtil {
    actual fun mkDirs(path: String): Boolean =
        // TODO setup a logger for util and log on error?
        NSFileManager.defaultManager.createDirectoryAtPath(path, false, null, null)

    actual fun deleteDirectory(path: String): Boolean = memScoped {
        // TODO setup a logger for util and log on error?
        val error = alloc<ObjCObjectVar<NSError?>>()
        return NSFileManager.defaultManager.removeItemAtPath(path, error.ptr)
    }

    actual fun isDirectoryNonEmpty(path: String): Boolean = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error.ptr)?.isNotEmpty() ?: false
    }
}
