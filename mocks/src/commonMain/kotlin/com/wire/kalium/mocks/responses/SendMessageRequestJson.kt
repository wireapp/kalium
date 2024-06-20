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

package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.api.base.authenticated.message.Parameters
import com.wire.kalium.network.api.base.authenticated.message.UserToClientToEncMsgMap

object SendMessageRequestJson {

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_CLIENT_1 = "60f85e4b15ad3786"
    private const val USER_1_CLIENT_1_MSG = "hello, world but encrypted for USER_1_client_1"

    private const val USER_1_CLIENT_2 = "6e323ab31554353b"
    private const val USER_1_CLIENT_2_MSG = "hello, world but encrypted for USER_1_client_2"

    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"
    private const val USER_2_CLIENT_1 = "32233lj33j3dfh7u"
    private const val USER_2_CLIENT_1_MSG = "hello, world but encrypted for USER_2_client_1"

    private const val USER_2_CLIENT_2 = "duf3eif09324wq5j"
    private const val USER_2_CLIENT_2_MSG = "hello, world but encrypted for USER_2_client_2"

    private val recipients: UserToClientToEncMsgMap = mapOf(
        USER_1 to
                mapOf(
                    USER_1_CLIENT_1 to USER_1_CLIENT_1_MSG,
                    USER_1_CLIENT_2 to USER_1_CLIENT_2_MSG
                ),
        USER_2 to
                mapOf(
                    USER_2_CLIENT_1 to USER_2_CLIENT_1_MSG,
                    USER_2_CLIENT_2 to USER_2_CLIENT_2_MSG
                )
    )

    private val defaultParametersJson = { serializable: Parameters.DefaultParameters ->
        """
        |  "sender": ${serializable.sender},
        |  "data": "${serializable.data}",
        |  "native_push": ${serializable.nativePush},
        |  "recipients": {
        |               "$USER_1": {
        |                   "$USER_1_CLIENT_1" : "$USER_1_CLIENT_1_MSG",
        |                   "$USER_1_CLIENT_2" : "$USER_1_CLIENT_2_MSG"
        |               },
        |               "$USER_2: {
        |                   "$USER_2_CLIENT_1" : "$USER_2_CLIENT_1_MSG",
        |                   "$USER_2_CLIENT_2" : "$USER_2_CLIENT_2_MSG"
        |               }
        |       },
        |  "transient": ${serializable.transient},
        |  "native_priority": ${serializable.priority}
        """.trimMargin()
    }

    private val reportMissingJsonProvider = {
        """
        |   "report_missing": [
        |           "$USER_1_CLIENT_2"
        |               ]
        """.trimMargin()
    }

    private val defaultParametersProvider = { serializable: Parameters.DefaultParameters ->
        """
        |   {
        |   ${defaultParametersJson(serializable).replace("\\s".toRegex(), "")}
        |   }
        """.trimMargin()
    }

    val validDefaultParameters = ValidJsonProvider(
        Parameters.DefaultParameters(
            sender = "sender-client-it",
            data = null,
            nativePush = true,
            recipients = recipients,
            transient = false,
            priority = MessagePriority.LOW
        ),
        defaultParametersProvider
    )

    val validReportSomeJsonJson = { serializable: Parameters.DefaultParameters ->
        """
        |   {
        |   ${
            defaultParametersJson(serializable)
                .replace("\\s".toRegex(), "")
        },
        |   ${
            reportMissingJsonProvider()
                .replace("\\s".toRegex(), "")
        }
        |   }
        """.trimMargin()
    }
}
