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

package com.wire.kalium.network.api.base.authenticated.keypackage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeyPackageList(
    @SerialName("key_packages")
    val keyPackages: List<KeyPackage>
)

@Serializable
data class ClaimedKeyPackageList(
    @SerialName("key_packages")
    val keyPackages: List<KeyPackageDTO>
)

@Serializable
data class KeyPackageDTO(
    @SerialName("client")
    val clientID: String,
    @SerialName("domain")
    val domain: String,
    @SerialName("key_package")
    val keyPackage: KeyPackage,
    @SerialName("key_package_ref")
    val keyPackageRef: KeyPackageRef,
    @SerialName("user")
    val userId: String
)

@Serializable
data class KeyPackageCountDTO(
    @SerialName("count") val count: Int
)
