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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateAssetFileTypeUseCaseTest {

    private val validate = ValidateAssetFileTypeUseCaseImpl()

    @Test
    fun givenAllowedFileNameExtension_whenInvoked_thenReturnsTrue() {
        assertTrue(validate(fileName = "name.txt", mimeType = "", allowedExtension = listOf("txt", "jpg")))
    }

    @Test
    fun givenDisallowedFileNameExtension_whenInvoked_thenReturnsFalse() {
        assertFalse(validate(fileName = "name.php", mimeType = "", allowedExtension = listOf("txt", "jpg")))
    }

    @Test
    fun givenFileNameWithoutExtensionAndAllowedMimeType_whenInvoked_thenDoesNotFallBackToMimeType() {
        assertFalse(validate(fileName = "name", mimeType = "text/plain", allowedExtension = listOf("txt")))
    }

    @Test
    fun givenEmptyFileNameAndAllowedMimeType_whenInvoked_thenDoesNotFallBackToMimeType() {
        assertFalse(validate(fileName = "", mimeType = "text/plain", allowedExtension = listOf("txt")))
    }

    @Test
    fun givenNullFileNameAndAllowedMimeType_whenInvoked_thenUsesMimeTypeMapping() {
        assertTrue(validate(fileName = null, mimeType = "text/plain", allowedExtension = listOf("txt")))
    }

    @Test
    fun givenNullFileNameAndUnknownMimeType_whenInvoked_thenReturnsFalse() {
        assertFalse(validate(fileName = null, mimeType = "unknown/type", allowedExtension = listOf("txt")))
    }

    @Test
    fun givenUppercaseExtensionAndLowercaseAllowedEntry_whenInvoked_thenMatchingRemainsCaseSensitive() {
        assertFalse(validate(fileName = "name.TXT", mimeType = "", allowedExtension = listOf("txt")))
    }

    @Test
    fun givenAllowedEntryContainsDot_whenInvoked_thenMatchingUsesOnlyTheParsedExtension() {
        assertFalse(validate(fileName = "name.txt", mimeType = "", allowedExtension = listOf(".txt")))
    }

    @Test
    fun givenMultipleDots_whenInvoked_thenOnlyTheLastExtensionIsMatched() {
        assertTrue(validate(fileName = "archive.tar.gz", mimeType = "", allowedExtension = listOf("gz")))
    }

    @Test
    fun givenNullFileNameAndImageJpegMimeType_whenInvoked_thenPreservesLastDuplicateMapEntry() {
        assertTrue(validate(fileName = null, mimeType = "image/jpeg", allowedExtension = listOf("pjp")))
        assertFalse(validate(fileName = null, mimeType = "image/jpeg", allowedExtension = listOf("jpg")))
    }
}
