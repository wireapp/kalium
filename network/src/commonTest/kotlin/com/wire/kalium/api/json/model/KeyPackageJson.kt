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

package com.wire.kalium.api.json.model

import com.wire.kalium.mocks.responses.ValidJsonProvider
import com.wire.kalium.network.api.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageDTO

object KeyPackageJson {

    val valid = ValidJsonProvider(
        ClaimedKeyPackageList(
            listOf(
                KeyPackageDTO(
                    "defkrr8e7grgsoufhg8",
                    "wire.com",
                    "keyPackage",
                    "keyPackageRef",
                    "fdf23116-42a5-472c-8316-e10655f5d11e"
                )
            )
        )
    ) {
        """
        |{
        |  "key_packages": [
        |     {
        |        "user": "${it.keyPackages[0].userId}",
        |        "client": "${it.keyPackages[0].clientID}",
        |        "domain": "${it.keyPackages[0].domain}",
        |        "key_package": "${it.keyPackages[0].keyPackage}",
        |        "key_package_ref": "${it.keyPackages[0].keyPackageRef}"
        |     }
        |  ]
        |}
        """.trimMargin()
    }

    fun keyPackageCountJson(count: Int) = ValidJsonProvider(
        KeyPackageCountDTO(count)

    ) {
        """{"count":${it.count}}""".trimMargin()
    }
}
