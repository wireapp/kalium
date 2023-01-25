/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonUtilsTest {

    @Test
    fun givenAFileName_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file.jpg"
        val expectedFileExtension = "jpg"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAFileNameWithMultipleExtensionDots_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = "some_dummy_file.tar.gz"
        val expectedFileExtension = "tar.gz"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAnEmptyFileName_whenGettingItsFileExtension_itReturnsNull() {
        val fileName = ""
        val expectedFileExtension = null

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }
}
