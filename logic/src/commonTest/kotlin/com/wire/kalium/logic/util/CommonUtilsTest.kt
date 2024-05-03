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

package com.wire.kalium.logic.util

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class CommonUtilsTest {

    @Test
    fun givenDurationOfLessThanAWeek_thenInWholeWeeksReturnsZero() {
        assertEquals(0, 6.days.inWholeWeeks)
    }

    @Test
    fun givenDurationOfMoreThanAWeek_thenInWholeWeeksReturnsOne() {
        assertEquals(1, 7.days.inWholeWeeks)
    }

    @Test
    fun givenDurationOfMoreThanTwoWeeks_thenInWholeWeeksReturnsTwo() {
        assertEquals(2, 15.days.inWholeWeeks)
    }

    @Test
    fun givenAFileName_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file.jpg"
        val expectedFileExtension = "jpg"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAFileNameWithMultipleDots_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = "some.dummy.file.jpg"
        val expectedFileExtension = "jpg"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }
    @Test
    fun givenAFileNameWithMultipleDotsAndStartingWithADot_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = ".dummy.file.jpg"
        val expectedFileExtension = "jpg"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAFileNameWithMultipleExtensionDots_whenGettingItsFileExtension_itReturnsItCorrectly() {
        val fileName = "some_dummy_file.tar.gz"
        // Most authors define extension in a way that doesn't allow more than one in the same file name,
        // .tar.gz actually represents nested transformations, .tar is only for informational purposes and `gz` is the final extension.
        val expectedFileExtension = "gz"

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAFileNameStartingWithADotAndWithoutExtension_whenGettingItsFileExtension_itReturnsNull() {
        val fileName = ".dummy"
        val expectedFileExtension = null

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }

    @Test
    fun givenAFileNameWithoutExtension_whenGettingItsFileExtension_itReturnsNull() {
        val fileName = "file"
        val expectedFileExtension = null

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

    @Test
    fun givenNameWithoutCopyCounterAndWithoutExtension_whenBuildingFileName_thenReturnTheSameName() {
        val name = "abc"
        val copyCounter = 0
        val extension = null
        val expected = "abc"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenNameWithCopyCounterAndWithoutExtension_whenBuildingFileName_thenReturnProperNameWithCopyCounter() {
        val name = "abc"
        val copyCounter = 2
        val extension = null
        val expected = "abc (2)"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenNameWithoutCopyCounterAndWithExtension_whenBuildingFileName_thenReturnProperNameWithExtension() {
        val name = "abc"
        val copyCounter = 0
        val extension = "jpg"
        val expected = "abc.jpg"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenNameWithCopyCounterAndWithExtension_whenBuildingFileName_thenReturnProperNameWithCopyCounterAndWithExtension() {
        val name = "abc"
        val copyCounter = 2
        val extension = "jpg"
        val expected = "abc (2).jpg"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenNameWithDotAndWithCopyCounterAndWithExtension_whenBuildingFileName_thenReturnProperNameWithCopyCounterAndWithExtension() {
        val name = "ab.c"
        val copyCounter = 2
        val extension = "jpg"
        val expected = "ab.c (2).jpg"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenNameWithBracketsAndWithCopyCounterAndWithExtension_whenBuildingFileName_thenReturnProperNameWithCopyCounterAndWithExtension() {
        val name = "ab(1)"
        val copyCounter = 2
        val extension = "jpg"
        val expected = "ab(1) (2).jpg"

        val result = buildFileName(name, extension, copyCounter)
        assertEquals(expected, result)
    }

    @Test
    fun givenAFileNameWithoutCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file.jpg"
        val expectedCoreName = "some_dummy_image_file"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithoutCopyCounterAndWithMultipleExtensionDots_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file.tar.gz"
        // Most authors define extension in a way that doesn't allow more than one in the same file name,
        // .tar.gz actually represents nested transformations, .tar is only for informational purposes and `gz` is the final extension.
        val expectedCoreName = "some_dummy_image_file.tar"
        val expectedFileExtension = "gz"
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file (2).jpg"
        val expectedCoreName = "some_dummy_image_file"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 2

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithoutCopyCounterAndWithoutExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file"
        val expectedCoreName = "some_dummy_image_file"
        val expectedFileExtension = null
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithCopyCounterAndWithoutExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file (2)"
        val expectedCoreName = "some_dummy_image_file"
        val expectedFileExtension = null
        val expectedCopyCounter = 2

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithBracketsAndWithoutCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file(1).jpg"
        val expectedCoreName = "some_dummy_image_file(1)"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameWithBracketsAndWithCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = "some_dummy_image_file(1) (2).jpg"
        val expectedCoreName = "some_dummy_image_file(1)"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 2

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameStartingWithADotWithCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = ".some_dummy_image_file (2).jpg"
        val expectedCoreName = ".some_dummy_image_file"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 2

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameStartingWithADotWithCopyCounterAndWithoutExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = ".some_dummy_image_file (2)"
        val expectedCoreName = ".some_dummy_image_file"
        val expectedFileExtension = null
        val expectedCopyCounter = 2

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameStartingWithADotWithoutCopyCounterAndWithExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = ".some_dummy_image_file.jpg"
        val expectedCoreName = ".some_dummy_image_file"
        val expectedFileExtension = "jpg"
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAFileNameStartingWithADotWithoutCopyCounterAndWithoutExtension_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = ".some_dummy_image_file"
        val expectedCoreName = ".some_dummy_image_file"
        val expectedFileExtension = null
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    @Test
    fun givenAnEmptyFileName_whenSplittingExtensionAndCopyCounter_itReturnsItCorrectly() {
        val fileName = ""
        val expectedCoreName = ""
        val expectedFileExtension = null
        val expectedCopyCounter = 0

        fileName.splitFileExtensionAndCopyCounter()
            .assertSplitResult(expectedCoreName, expectedCopyCounter, expectedFileExtension)
    }

    private fun Triple<String, Int, String?>.assertSplitResult(
        expectedCoreName: String,
        expectedCopyCounter: Int,
        expectedExtension: String?
    ) {
        val (coreName, copyCounter, extension) = this
        assertEquals(expectedCoreName, coreName)
        assertEquals(expectedCopyCounter, copyCounter)
        assertEquals(expectedExtension, extension)
    }
}
