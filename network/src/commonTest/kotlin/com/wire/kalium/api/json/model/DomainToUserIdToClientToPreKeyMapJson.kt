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
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.base.model.UserId

object DomainToUserIdToClientToPreKeyMapJson {

    private const val DOMAIN_1 = "domain1.example.com"
    private const val DOMAIN_2 = "domain2.example.com"

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_CLIENT = "60f85e4b15ad3786"
    private val USER_1_CLIENT_PREYKEY = PreKeyDTO(key = "preKey1CoQBYIOjl7hw0D8YRNq", id = 1)

    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"
    private const val USER_2_CLIENT = "32233lj33j3dfh7u"
    private val USER_2_CLIENT_PREYKEY = PreKeyDTO(key = "preKey2ANWARqEvoQI6l9hw0D", id = 2)

    private val USER_3 = UserId("user300d0-000b-9c1a-000d-a4130002c121", "domain3.example.com")

    private val jsonProvider = { _: DomainToUserIdToClientsToPreKeyMap ->
        """
            |{
            |  "$DOMAIN_1": {
            |    "$USER_1": {
            |      "$USER_1_CLIENT": {
            |        "key": "${USER_1_CLIENT_PREYKEY.key}",
            |        "id": ${USER_1_CLIENT_PREYKEY.id}
            |      }
            |    }
            |  },
            |  "$DOMAIN_2": {
            |    "$USER_2": {
            |      "$USER_2_CLIENT": {
            |        "key": "${USER_2_CLIENT_PREYKEY.key}",
            |        "id": "${USER_2_CLIENT_PREYKEY.id}"
            |      }
            |    }
            |  }
            |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        mapOf(
            DOMAIN_1 to
                    mapOf(
                        USER_1 to
                                mapOf(USER_1_CLIENT to USER_1_CLIENT_PREYKEY)
                    ),
            DOMAIN_2 to
                    mapOf(
                        USER_2 to
                                mapOf(USER_2_CLIENT to USER_2_CLIENT_PREYKEY)
                    )
        ),
        jsonProvider
    )

    val validV4 = ValidJsonProvider(
        ListPrekeysResponse(
            listOf(USER_3),
            mapOf(
                DOMAIN_1 to
                        mapOf(
                            USER_1 to
                                    mapOf(USER_1_CLIENT to USER_1_CLIENT_PREYKEY)
                        ),
                DOMAIN_2 to
                        mapOf(
                            USER_2 to
                                    mapOf(USER_2_CLIENT to USER_2_CLIENT_PREYKEY)
                        )
            )
        )
    ) {
        """
            |{
            |   "failed_to_list": [
            |       {
            |           "domain": "${it.failedToList?.first()?.domain}",
            |           "id": "${it.failedToList?.first()?.value}"
            |       }
            |   ],
            |   "qualified_user_client_prekeys": {
            |       "$DOMAIN_1": {
            |           "$USER_1": {
            |               "$USER_1_CLIENT": {
            |                   "key": "${USER_1_CLIENT_PREYKEY.key}",
            |                   "id": ${USER_1_CLIENT_PREYKEY.id}
            |               }
            |           }
            |       },
            |       "$DOMAIN_2": {
            |           "$USER_2": {
            |               "$USER_2_CLIENT": {
            |                   "key": "${USER_2_CLIENT_PREYKEY.key}",
            |                   "id": "${USER_2_CLIENT_PREYKEY.id}"
            |               }
            |           }
            |       }
            |  }
            |}  
        """.trimMargin()
    }
}
