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
package com.wire.kalium.api.v3

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.AnyResponseProvider
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.v3.authenticated.ConversationApiV3
import com.wire.kalium.network.utils.mapSuccess
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ConversationV3ResponseTest : ApiTest() {

    @Test
    fun givenConversationV3ResponseWithOnlyAccessRoleV2_whenMappingToConversation_thenItMapsCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            conversationV3ResponseWithAccessRoleV2.rawJson,
            statusCode = HttpStatusCode.OK
        )

        val conversationApiV3 = ConversationApiV3(networkClient)

        val response = conversationApiV3.fetchConversationsListDetails(listOf())

        response.mapSuccess {
            assertEquals(
                setOf(
                    ConversationAccessRoleDTO.TEAM_MEMBER,
                    ConversationAccessRoleDTO.SERVICE
                ),
                it.conversationsFound.first().accessRole
            )
        }
    }

    @Test
    fun givenConversationV3ResponseWithOnlyAccessRole_whenMappingToConversation_thenItMapsCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            conversationV3ResponseWithAccessRole.rawJson,
            statusCode = HttpStatusCode.OK
        )

        val conversationApiV3 = ConversationApiV3(networkClient)

        val response = conversationApiV3.fetchConversationsListDetails(listOf())

        response.mapSuccess {
            assertEquals(
                setOf(
                    ConversationAccessRoleDTO.TEAM_MEMBER,
                    ConversationAccessRoleDTO.SERVICE
                ),
                it.conversationsFound.first().accessRole
            )
        }
    }

    @Test
    fun givenConversationV3ResponseWithBothAccessRole_whenMappingToConversation_thenItMapsCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            conversationV3ResponseWithBothAccessRole.rawJson,
            statusCode = HttpStatusCode.OK
        )

        val conversationApiV3 = ConversationApiV3(networkClient)

        val response = conversationApiV3.fetchConversationsListDetails(listOf())

        response.mapSuccess {
            assertEquals(
                setOf(
                    ConversationAccessRoleDTO.TEAM_MEMBER,
                    ConversationAccessRoleDTO.SERVICE
                ),
                it.conversationsFound.first().accessRole
            )
        }
    }

    private companion object {
        val conversationV3ResponseWithAccessRoleV2 = AnyResponseProvider(data = "") {
            """
        |{
        |    "failed": [],
        |    "found": [
        |        {
        |            "access": [
        |                "invite"
        |            ],
        |            "access_role_v2": [
        |                "team_member",
        |                "service"
        |            ],
        |            "creator": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
        |            "last_event": "0.0",
        |            "last_event_time": "1970-01-01T00:00:00.000Z",
        |            "members": {
        |                "others": [
        |                    {
        |                        "conversation_role": "wire_member",
        |                        "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613",
        |                        "qualified_id": {
        |                            "domain": "bella.wire.link",
        |                            "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613"
        |                        },
        |                        "status": 0
        |                    }
        |                ],
        |                "self": {
        |                    "conversation_role": "wire_admin",
        |                    "hidden": false,
        |                    "hidden_ref": null,
        |                    "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |                    "otr_archived": false,
        |                    "otr_archived_ref": null,
        |                    "otr_muted_ref": null,
        |                    "otr_muted_status": null,
        |                    "qualified_id": {
        |                        "domain": "anta.wire.link",
        |                        "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |                    },
        |                    "service": null,
        |                    "status": 0,
        |                    "status_ref": "0.0",
        |                    "status_time": "1970-01-01T00:00:00.000Z"
        |                }
        |            },
        |            "message_timer": null,
        |            "name": "test-anta-grp",
        |            "protocol": "proteus",
        |            "qualified_id": {
        |                "domain": "anta.wire.link",
        |                "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b"
        |            },
        |            "receipt_mode": null,
        |            "team": null,
        |            "type": 0
        |        }
        |    ],
        |    "not_found": []
        |}
        """.trimMargin()
        }

        val conversationV3ResponseWithAccessRole = AnyResponseProvider(data = "") {
            """
        |{
        |    "failed": [],
        |    "found": [
        |        {
        |            "access": [
        |                "invite"
        |            ],
        |            "access_role": [
        |                "team_member",
        |                "service"
        |            ],
        |            "creator": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
        |            "last_event": "0.0",
        |            "last_event_time": "1970-01-01T00:00:00.000Z",
        |            "members": {
        |                "others": [
        |                    {
        |                        "conversation_role": "wire_member",
        |                        "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613",
        |                        "qualified_id": {
        |                            "domain": "bella.wire.link",
        |                            "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613"
        |                        },
        |                        "status": 0
        |                    }
        |                ],
        |                "self": {
        |                    "conversation_role": "wire_admin",
        |                    "hidden": false,
        |                    "hidden_ref": null,
        |                    "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |                    "otr_archived": false,
        |                    "otr_archived_ref": null,
        |                    "otr_muted_ref": null,
        |                    "otr_muted_status": null,
        |                    "qualified_id": {
        |                        "domain": "anta.wire.link",
        |                        "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |                    },
        |                    "service": null,
        |                    "status": 0,
        |                    "status_ref": "0.0",
        |                    "status_time": "1970-01-01T00:00:00.000Z"
        |                }
        |            },
        |            "message_timer": null,
        |            "name": "test-anta-grp",
        |            "protocol": "proteus",
        |            "qualified_id": {
        |                "domain": "anta.wire.link",
        |                "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b"
        |            },
        |            "receipt_mode": null,
        |            "team": null,
        |            "type": 0
        |        }
        |    ],
        |    "not_found": []
        |}
        """.trimMargin()
        }

        val conversationV3ResponseWithBothAccessRole = AnyResponseProvider(data = "") {
            """
        |{
        |    "failed": [],
        |    "found": [
        |        {
        |            "access": [
        |                "invite"
        |            ],
        |            "access_role": [
        |                "team_member",
        |                "service"
        |            ],
        |            "access_role_v2": [
        |                "team_member",
        |                "non_team_member",
        |                "service"
        |            ],
        |            "creator": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |            "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b",
        |            "last_event": "0.0",
        |            "last_event_time": "1970-01-01T00:00:00.000Z",
        |            "members": {
        |                "others": [
        |                    {
        |                        "conversation_role": "wire_member",
        |                        "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613",
        |                        "qualified_id": {
        |                            "domain": "bella.wire.link",
        |                            "id": "22dfd5cc-11ae-4a9d-9046-ba27585f4613"
        |                        },
        |                        "status": 0
        |                    }
        |                ],
        |                "self": {
        |                    "conversation_role": "wire_admin",
        |                    "hidden": false,
        |                    "hidden_ref": null,
        |                    "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b",
        |                    "otr_archived": false,
        |                    "otr_archived_ref": null,
        |                    "otr_muted_ref": null,
        |                    "otr_muted_status": null,
        |                    "qualified_id": {
        |                        "domain": "anta.wire.link",
        |                        "id": "f4680835-2cfe-4d4d-8491-cbb201bd5c2b"
        |                    },
        |                    "service": null,
        |                    "status": 0,
        |                    "status_ref": "0.0",
        |                    "status_time": "1970-01-01T00:00:00.000Z"
        |                }
        |            },
        |            "message_timer": null,
        |            "name": "test-anta-grp",
        |            "protocol": "proteus",
        |            "qualified_id": {
        |                "domain": "anta.wire.link",
        |                "id": "ebafd3d4-1548-49f2-ac4e-b2757e6ca44b"
        |            },
        |            "receipt_mode": null,
        |            "team": null,
        |            "type": 0
        |        }
        |    ],
        |    "not_found": []
        |}
        """.trimMargin()
        }
    }
}
