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
package com.wire.kalium.logic.feature.asset

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateAssetFileTypeUseCaseTest {

    @Test
    fun givenRegularFileNameWithAllowedExtension_whenInvoke_thenBeApproved() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = "name.txt", mimeType = "", allowedExtension = listOf("txt", "jpg"))

        assertTrue(result)
    }

    @Test
    fun givenRegularFileNameWithNOTAllowedExtension_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = "name.php", mimeType = "", allowedExtension = listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenRegularFileNameWithoutExtension_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = "name", mimeType = "", allowedExtension = listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenNullFileName_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = null, mimeType = "", allowedExtension = listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenFileNameIs() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = null, mimeType = "image/jpg", allowedExtension = listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenNullFileNameAndValidMimeType_whenInvoke_thenMimeTypeIsChecked() = runTest {
        val (_, validate) = arrange {}

        val result = validate(fileName = null, mimeType = "image/jpg", allowedExtension = listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenNullFileNameAndInvalidMimeType_whenInvoke_thenMimeTypeIsChecked() = runTest {
        val (_, validate) = arrange {}

        val result = validate(
            fileName = null,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            allowedExtension = listOf("txt", "jpg")
        )

        assertFalse(result)
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) {
        fun arrange() = block().run {
            this@Arrangement to ValidateAssetFileTypeUseCaseImpl()
        }
    }
}
