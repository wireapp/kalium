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

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsMap

object DomainToUserIdToClientsMapJson {

    private const val DOMAIN_1 = "domain1.example.com"

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_client_1 = "60f85e4b15ad3786"
    private const val USER_1_client_2 = "6e323ab31554353b"

    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"
    private const val USER_2_client_1 = "32233lj33j3dfh7u"
    private const val USER_2_client_2 = "duf3eif09324wq5j"

    private val jsonProvider = { _: DomainToUserIdToClientsMap ->
        """
        |{
        |  "$DOMAIN_1": {
        |    "$USER_1": [
        |      "$USER_1_client_1",
        |      "$USER_1_client_2"
        |    ],
        |    "$USER_2": [
        |      "$USER_2_client_1",
        |      "$USER_2_client_2"
        |    ]
        |  }
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        mapOf(
            DOMAIN_1 to
                    mapOf(
                        USER_1 to listOf(USER_1_client_1, USER_1_client_2),
                        USER_2 to listOf(USER_2_client_1, USER_2_client_2)
                    )
        ),
        jsonProvider
    )
}
