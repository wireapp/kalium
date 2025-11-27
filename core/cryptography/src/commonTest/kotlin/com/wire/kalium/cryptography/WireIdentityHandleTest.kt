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
package com.wire.kalium.cryptography

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WireIdentityHandleTest {

    private fun testCreatingWireIdentityHandle(
        rawValue: String,
        expectedScheme: String,
        expectedHandle: String,
        expectedDomain: String,
    ) = runTest {
        val wireIdentityHandle = WireIdentity.Handle.fromString(rawValue, expectedDomain)
        assertEquals(expectedScheme, wireIdentityHandle.scheme)
        assertEquals(expectedHandle, wireIdentityHandle.handle)
        assertEquals(expectedDomain, wireIdentityHandle.domain)
    }

    @Test
    fun givenRawHandleWithSchemeAtSignDomain_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "sch.eme://%40han.dle@dom.ain",
            expectedScheme = "sch.eme",
            expectedHandle = "han.dle",
            expectedDomain = "dom.ain"
        )

    @Test
    fun givenRawHandleWithSchemeAtSign_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "sch.eme://%40han.dle",
            expectedScheme = "sch.eme",
            expectedHandle = "han.dle",
            expectedDomain = ""
        )

    @Test
    fun givenRawHandleWithSchemeDomain_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "sch.eme://han.dle@dom.ain",
            expectedScheme = "sch.eme",
            expectedHandle = "han.dle",
            expectedDomain = "dom.ain"
        )

    @Test
    fun givenRawHandleWithScheme_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "sch.eme://han.dle",
            expectedScheme = "sch.eme",
            expectedHandle = "han.dle",
            expectedDomain = ""
        )

    @Test
    fun givenRawHandleWithAtSignDomain_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "%40han.dle@dom.ain",
            expectedScheme = "",
            expectedHandle = "han.dle",
            expectedDomain = "dom.ain"
        )

    @Test
    fun givenRawHandleWithAtSign_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "%40han.dle",
            expectedScheme = "",
            expectedHandle = "han.dle",
            expectedDomain = ""
        )

    @Test
    fun givenRawHandleWithDomain_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "han.dle@dom.ain",
            expectedScheme = "",
            expectedHandle = "han.dle",
            expectedDomain = "dom.ain"
        )

    @Test
    fun givenRawHandle_whenCreatingWireIdentityHandle_thenItIsCorrectlyParsed() =
        testCreatingWireIdentityHandle(
            rawValue = "han.dle",
            expectedScheme = "",
            expectedHandle = "han.dle",
            expectedDomain = ""
        )
}
