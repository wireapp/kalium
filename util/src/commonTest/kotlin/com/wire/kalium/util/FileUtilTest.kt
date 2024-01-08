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

import com.wire.kalium.util.string.IgnoreJS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

expect object FileTestHelper {
    fun createRandomDirectory(): String
    fun createRandomFileAt(path: String)
    fun directoryExists(path: String): Boolean
}

@IgnoreJS
class FileUtilTest {

    @Test
    fun givenEmptyDirectory_thenIsDirectoryNonEmptyReturnsFalse() {
        val path = FileTestHelper.createRandomDirectory()
        assertFalse(FileUtil.isDirectoryNonEmpty(path))
    }

    @Test
    fun givenNonExistingDirectory_thenIsDirectoryNonEmptyReturnsFalse() {
        val path = "/non/existing/path"
        assertFalse(FileUtil.isDirectoryNonEmpty(path))
    }

    @Test
    fun givenNonEmptyDirectory_thenIsDirectoryNonEmptyReturnsTrue() {
        val path = FileTestHelper.createRandomDirectory()
        FileTestHelper.createRandomFileAt(path)
        assertTrue(FileUtil.isDirectoryNonEmpty(path))
    }

    @Test
    fun givenNonEmptyDirectory_whenCallingDeleteDirectory_thenDirectoryNoLongerExists() {
        val path = FileTestHelper.createRandomDirectory()
        assertTrue(FileTestHelper.directoryExists(path))
        FileUtil.deleteDirectory(path)
        assertFalse(FileTestHelper.directoryExists(path))
    }

    @Test
    fun givenNonExistingDirectory_whenMkDir_thenDirectoryExists() {
        val path = FileTestHelper.createRandomDirectory()
        FileUtil.deleteDirectory(path)
        assertFalse(FileTestHelper.directoryExists(path))
        FileUtil.mkDirs(path)
        assertTrue(FileTestHelper.directoryExists(path))
    }
}
