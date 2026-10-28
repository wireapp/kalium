/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.configuration

import platform.Foundation.NSProcessInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AppleDeviceModelNameTest {

    @Test
    fun givenAKnownIdentifier_whenGettingTheModelName_thenTheMarketingNameIsReturned() {
        assertEquals("iPhone 16", iosDeviceModelName("iPhone17,3"))
        assertEquals("iPhone 18 Pro Max", iosDeviceModelName("iPhone19,7"))
        assertEquals("iPad Pro 11-inch (M4)", iosDeviceModelName("iPad16,4"))
    }

    @Test
    fun givenAnUnknownIdentifier_whenGettingTheModelName_thenTheIdentifierIsReturned() {
        assertEquals("iPhone99,1", iosDeviceModelName("iPhone99,1"))
    }

    @Test
    fun givenASimulator_whenGettingTheDeviceModelName_thenTheSimulatedModelIsNamed() {
        val identifier = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String ?: return

        assertEquals("${iosDeviceModelName(identifier)} Simulator", ClientConfigImpl().deviceModelName())
    }
}
