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

import com.wire.kalium.network.api.authenticated.message.QualifiedSendMessageResponse

object QualifiedSendMessageResponseJson {

    private const val TIME = "2021-05-31T10:52:02.671Z"

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_client_1 = "60f85e4b15ad3786"
    private const val USER_1_client_2 = "6e323ab31554353b"
    private const val DOMAIN_1 = "domain1.com"
    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"

    private const val USER_2_client_1 = "32233lj33j3dfh7u"
    private const val USER_2_client_2 = "duf3eif09324wq5j"
    private const val DOMAIN_2 = "domain2.com"

    private val USER_MAP = mapOf(
        DOMAIN_1 to mapOf(USER_1 to arrayListOf(USER_1_client_1, USER_1_client_2)),
        DOMAIN_2 to mapOf(USER_2 to arrayListOf(USER_2_client_1, USER_2_client_2)),
    )

    private val emptyResponse = { _: QualifiedSendMessageResponse ->
        """
        |{
        |   "time": "$TIME"
        |   "missing" : {},
        |   "redundant" : {},
        |   "deleted" : {}
        |}
        """.trimMargin()
    }

    private val v0_missingProvider = { _: QualifiedSendMessageResponse ->
        """
            |{
            |   "time": "$TIME"
            |   "redundant" : {},
            |   "missing": {
            |       "$DOMAIN_1": {
            |           "$USER_1": [
            |               "$USER_1_client_1",
            |               "$USER_1_client_2"
            |          ]
            |       },
            |       "$DOMAIN_2": {
            |           "$USER_2": [
            |               "$USER_2_client_1",
            |               "$USER_2_client_2"
            |          ]
            |       }
            |   },
            |   "deleted" : {}
            |}
        """.trimMargin()
    }

    private val v3_failedToSend = { _: QualifiedSendMessageResponse ->
        """
            |{
            |   "time": "$TIME"
            |   "missing" : {},
            |   "failed_to_confirm_clients": {
            |       "$DOMAIN_1": {
            |           "$USER_1": [
            |               "$USER_1_client_1",
            |               "$USER_1_client_2"
            |          ]
            |       },
            |       "$DOMAIN_2": {
            |           "$USER_2": [
            |               "$USER_2_client_1",
            |               "$USER_2_client_2"
            |          ]
            |       }
            |   },
            |   "redundant" : {}
            |   "deleted" : {}
            |}
        """.trimMargin()
    }

    private val v0_redundantProvider = { _: QualifiedSendMessageResponse ->
        """
            |{
            |   "time": "$TIME"
            |   "missing" : {},
            |   "redundant": {
            |       "$DOMAIN_1": {
            |           "$USER_1": [
            |               "$USER_1_client_1",
            |               "$USER_1_client_2"
            |          ]
            |       },
            |       "$DOMAIN_2": {
            |           "$USER_2": [
            |               "$USER_2_client_1",
            |               "$USER_2_client_2"
            |          ]
            |       }
            |   },
            |   "deleted" : {}
            |}
        """.trimMargin()
    }

    private val v0_deletedProvider = { _: QualifiedSendMessageResponse ->
        """
            |{
            |   "time": "$TIME"
            |   "redundant" : {},
            |   "deleted": {
            |       "$DOMAIN_1": {
            |           "$USER_1": [
            |               "$USER_1_client_1",
            |               "$USER_1_client_2"
            |          ]
            |       },
            |       "$DOMAIN_2": {
            |           "$USER_2": [
            |               "$USER_2_client_1",
            |               "$USER_2_client_2"
            |          ]
            |       }
            |   },
            |   "missing" : {}
            |}
        """.trimMargin()
    }

    val validMessageSentJson = ValidJsonProvider(
        QualifiedSendMessageResponse.MessageSent(TIME, mapOf(), mapOf(), mapOf()),
        emptyResponse
    )

    val missingUsersResponse = ValidJsonProvider(
        QualifiedSendMessageResponse.MissingDevicesResponse(TIME, missing = USER_MAP, mapOf(), mapOf()),
        v0_missingProvider
    )

    val deletedUsersResponse = ValidJsonProvider(
        QualifiedSendMessageResponse.MissingDevicesResponse(TIME, mapOf(), mapOf(), deleted = USER_MAP),
        v0_deletedProvider
    )

    val redundantUsersResponse = ValidJsonProvider(
        QualifiedSendMessageResponse.MissingDevicesResponse(TIME, mapOf(), redundant = USER_MAP, mapOf()),
        v0_redundantProvider
    )

    val failedSentUsersResponse = ValidJsonProvider(
        QualifiedSendMessageResponse.MissingDevicesResponse(TIME, mapOf(), mapOf(), mapOf(), USER_MAP),
        v3_failedToSend
    )
}
