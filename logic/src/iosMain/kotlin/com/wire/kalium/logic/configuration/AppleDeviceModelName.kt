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

internal actual fun appleDeviceModelName(): String {
    val simulatorIdentifier = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String
    val identifier = simulatorIdentifier ?: sysctlString("hw.machine") ?: return "N/A"
    val name = iosDeviceModelName(identifier)
    return if (simulatorIdentifier != null) "$name Simulator" else name
}

/**
 * Apple's marketing name for a device identifier such as `iPhone17,3`, or the identifier itself
 * for devices released after this list was last updated.
 */
internal fun iosDeviceModelName(identifier: String): String = modelNamesByIdentifier[identifier] ?: identifier

// Devices that can run iOS 17 or later.
private val modelNamesByIdentifier: Map<String, String> = listOf(
    "iPhone XS" to listOf("iPhone11,2"),
    "iPhone XS Max" to listOf("iPhone11,4", "iPhone11,6"),
    "iPhone XR" to listOf("iPhone11,8"),
    "iPhone 11" to listOf("iPhone12,1"),
    "iPhone 11 Pro" to listOf("iPhone12,3"),
    "iPhone 11 Pro Max" to listOf("iPhone12,5"),
    "iPhone SE (2nd generation)" to listOf("iPhone12,8"),
    "iPhone 12 mini" to listOf("iPhone13,1"),
    "iPhone 12" to listOf("iPhone13,2"),
    "iPhone 12 Pro" to listOf("iPhone13,3"),
    "iPhone 12 Pro Max" to listOf("iPhone13,4"),
    "iPhone 13 Pro" to listOf("iPhone14,2"),
    "iPhone 13 Pro Max" to listOf("iPhone14,3"),
    "iPhone 13 mini" to listOf("iPhone14,4"),
    "iPhone 13" to listOf("iPhone14,5"),
    "iPhone SE (3rd generation)" to listOf("iPhone14,6"),
    "iPhone 14" to listOf("iPhone14,7"),
    "iPhone 14 Plus" to listOf("iPhone14,8"),
    "iPhone 14 Pro" to listOf("iPhone15,2"),
    "iPhone 14 Pro Max" to listOf("iPhone15,3"),
    "iPhone 15" to listOf("iPhone15,4"),
    "iPhone 15 Plus" to listOf("iPhone15,5"),
    "iPhone 15 Pro" to listOf("iPhone16,1"),
    "iPhone 15 Pro Max" to listOf("iPhone16,2"),
    "iPhone 16 Pro" to listOf("iPhone17,1"),
    "iPhone 16 Pro Max" to listOf("iPhone17,2"),
    "iPhone 16" to listOf("iPhone17,3"),
    "iPhone 16 Plus" to listOf("iPhone17,4"),
    "iPhone 16e" to listOf("iPhone17,5"),
    "iPhone 17 Pro" to listOf("iPhone18,1"),
    "iPhone 17 Pro Max" to listOf("iPhone18,2"),
    "iPhone 17" to listOf("iPhone18,3"),
    "iPhone Air" to listOf("iPhone18,4"),
    "iPhone 17e" to listOf("iPhone18,5"),
    "iPhone 18 Pro" to listOf("iPhone19,2"),
    "iPhone 18 Pro Max" to listOf("iPhone19,3", "iPhone19,7"),
    "iPhone Duo" to listOf("iPhone19,4"),
    "iPad Pro 12.9-inch (2nd generation)" to listOf("iPad7,1", "iPad7,2"),
    "iPad Pro 10.5-inch" to listOf("iPad7,3", "iPad7,4"),
    "iPad (6th generation)" to listOf("iPad7,5", "iPad7,6"),
    "iPad (7th generation)" to listOf("iPad7,11", "iPad7,12"),
    "iPad Pro 11-inch (1st generation)" to listOf("iPad8,1", "iPad8,2", "iPad8,3", "iPad8,4"),
    "iPad Pro 12.9-inch (3rd generation)" to listOf("iPad8,5", "iPad8,6", "iPad8,7", "iPad8,8"),
    "iPad Pro 11-inch (2nd generation)" to listOf("iPad8,9", "iPad8,10"),
    "iPad Pro 12.9-inch (4th generation)" to listOf("iPad8,11", "iPad8,12"),
    "iPad mini (5th generation)" to listOf("iPad11,1", "iPad11,2"),
    "iPad Air (3rd generation)" to listOf("iPad11,3", "iPad11,4"),
    "iPad (8th generation)" to listOf("iPad11,6", "iPad11,7"),
    "iPad (9th generation)" to listOf("iPad12,1", "iPad12,2"),
    "iPad Air (4th generation)" to listOf("iPad13,1", "iPad13,2"),
    "iPad Pro 11-inch (3rd generation)" to listOf("iPad13,4", "iPad13,5", "iPad13,6", "iPad13,7"),
    "iPad Pro 12.9-inch (5th generation)" to listOf("iPad13,8", "iPad13,9", "iPad13,10", "iPad13,11"),
    "iPad Air (5th generation)" to listOf("iPad13,16", "iPad13,17"),
    "iPad (10th generation)" to listOf("iPad13,18", "iPad13,19"),
    "iPad mini (6th generation)" to listOf("iPad14,1", "iPad14,2"),
    "iPad Pro 11-inch (4th generation)" to listOf("iPad14,3", "iPad14,4"),
    "iPad Pro 12.9-inch (6th generation)" to listOf("iPad14,5", "iPad14,6"),
    "iPad Air 11-inch (M2)" to listOf("iPad14,8", "iPad14,9"),
    "iPad Air 13-inch (M2)" to listOf("iPad14,10", "iPad14,11"),
    "iPad Air 11-inch (M3)" to listOf("iPad15,3", "iPad15,4"),
    "iPad Air 13-inch (M3)" to listOf("iPad15,5", "iPad15,6"),
    "iPad (A16)" to listOf("iPad15,7", "iPad15,8"),
    "iPad mini (A17 Pro)" to listOf("iPad16,1", "iPad16,2"),
    "iPad Pro 11-inch (M4)" to listOf("iPad16,3", "iPad16,4"),
    "iPad Pro 13-inch (M4)" to listOf("iPad16,5", "iPad16,6"),
    "iPad Air 11-inch (M4)" to listOf("iPad16,8", "iPad16,9"),
    "iPad Air 13-inch (M4)" to listOf("iPad16,10", "iPad16,11"),
    "iPad Pro 11-inch (M5)" to listOf("iPad17,1", "iPad17,2"),
    "iPad Pro 13-inch (M5)" to listOf("iPad17,3", "iPad17,4"),
).flatMap { (name, identifiers) -> identifiers.map { it to name } }.toMap()
