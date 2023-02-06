/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.api.base.authenticated.message.QualifiedUserToClientToEncMsgMap
import com.wire.kalium.network.api.base.model.UserId
import io.ktor.utils.io.core.toByteArray

object QualifiedSendMessageRequestJson {

    private val USER_1 = UserId("user10d0-000b-9c1a-000d-a4130002c221", "example.com")
    private const val USER_1_CLIENT_1 = "60f85e4b15ad3786"
    private val USER_1_CLIENT_1_MSG = "hello, world but encrypted for USER_1_client_1".toByteArray()

    private const val USER_1_CLIENT_2 = "6e323ab31554353b"
    private val USER_1_CLIENT_2_MSG = "hello, world but encrypted for USER_1_client_2".toByteArray()

    private val USER_2 = UserId("user200d0-000b-9c1a-000d-a4130002c221", "example.com")
    private const val USER_2_CLIENT_1 = "32233lj33j3dfh7u"
    private val USER_2_CLIENT_1_MSG = "hello, world but encrypted for USER_2_client_1".toByteArray()

    private const val USER_2_CLIENT_2 = "duf3eif09324wq5j"
    private val USER_2_CLIENT_2_MSG = "hello, world but encrypted for USER_2_client_2".toByteArray()

    private val recipients: QualifiedUserToClientToEncMsgMap = mapOf(
        Pair(
            USER_1, mapOf(
                Pair(USER_1_CLIENT_1, USER_1_CLIENT_1_MSG),
                Pair(USER_1_CLIENT_2, USER_1_CLIENT_2_MSG),
            )
        ),
        Pair(
            USER_2, mapOf(
                Pair(USER_2_CLIENT_1, USER_2_CLIENT_1_MSG),
                Pair(USER_2_CLIENT_2, USER_1_CLIENT_2_MSG),
            )
        )
    )

    private val defaultParametersJson = { serializable: MessageApi.Parameters.QualifiedDefaultParameters ->
        """
        |  "sender": ${serializable.sender},
        |  "data": "${serializable.externalBlob?.decodeToString()}",
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

    private val defaultParametersProvider = { serializable: MessageApi.Parameters.QualifiedDefaultParameters ->
        """
        |   {
        |   ${defaultParametersJson(serializable).replace("\\s".toRegex(), "")}
        |   }
        """.trimMargin()
    }

    val validDefaultParameters = ValidJsonProvider(
        MessageApi.Parameters.QualifiedDefaultParameters(
            sender = "sender-client-it",
            externalBlob = "blob-id".toByteArray(),
            nativePush = true,
            recipients = recipients,
            transient = false,
            priority = MessagePriority.LOW,
            messageOption = MessageApi.QualifiedMessageOption.ReportAll
        ), defaultParametersProvider
    )

    val validReportSomeJsonJson = { serializable: MessageApi.Parameters.QualifiedDefaultParameters ->
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
