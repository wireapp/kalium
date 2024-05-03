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

package com.wire.kalium.model

import com.wire.kalium.api.json.FaultyJsonProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO

object PreKeyJson {
    val valid = ValidJsonProvider(
        PreKeyDTO(
            900,
            "preKeyData"
        )
    ) {
        """
        |{
        |  "id": ${it.id},
        |  "key": "${it.key}"
        |}
        """.trimMargin()
    }

    val missingId = FaultyJsonProvider(
        """
        |{
        |  "key": "preKeyData"
        |}
        """.trimMargin()
    )

    val wrongIdFormat = FaultyJsonProvider(
        """
        |{
        |  "id": "thisIsAStringInsteadOfAnInteger",
        |  "key": "preKeyData"
        |}
        """.trimMargin()
    )

    val missingKey = FaultyJsonProvider(
        """
        |{
        |  "id": 123
        |}
        """.trimMargin()
    )

    val wrongKeyFormat = FaultyJsonProvider(
        """
        |{
        |  "id": 900,
        |  "key": 42
        |}
        """.trimMargin()
    )
}
