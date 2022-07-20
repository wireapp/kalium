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
    fun givenAnEmptyFileName_whenGettingItsFileExtension_itReturnsVoid() {
        val fileName = ""
        val expectedFileExtension = ""

        val fileExtension = fileName.fileExtension()

        assertEquals(expectedFileExtension, fileExtension)
    }
}
