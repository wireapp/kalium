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

        val result = validate("name.txt", listOf("txt", "jpg"))

        assertTrue(result)
    }

    @Test
    fun givenRegularFileNameWithNOTAllowedExtension_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate("name.php", listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenRegularFileNameWithoutExtension_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate("name", listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenNullFileName_whenInvoke_thenBeRestricted() = runTest {
        val (_, validate) = arrange {}

        val result = validate(null, listOf("txt", "jpg"))

        assertFalse(result)
    }

    @Test
    fun givenRegularFileNameWithFewExtensions_whenInvoke_thenEachExtensionIsChecked() = runTest {
        val (_, validate) = arrange {}

        val result1 = validate("name.php.txt", listOf("txt", "jpg"))
        val result2 = validate("name.txt.php", listOf("txt", "jpg"))
        val result3 = validate("name..txt.jpg", listOf("txt", "jpg"))
        val result4 = validate("name.txt.php.txt.jpg", listOf("txt", "jpg"))

        assertFalse(result1)
        assertFalse(result2)
        assertFalse(result3)
        assertFalse(result4)
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
