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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.feature.session.DoesValidSessionExistUseCaseTest.Arrangement.Companion.TEST_USER_ID
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser.OTHER_USER_ID_2
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class SendMessagePartialFailureMapperTest {

    private val mapper = SendMessagePartialFailureMapperImpl()

    @Test
    fun testFromDTOMapping() {
        assertEquals(
            MessageSent("2022-04-21T20:56:22.393Z", listOf(TEST_USER_ID, OTHER_USER_ID_2)), mapper.fromDTO(RESULT_DTO)
        )
    }

    @Test
    fun testFromMlsDTOMapping() {
        val expectedUsersFailedToSend = listOf(TEST_USER_ID, OTHER_USER_ID_2)
        assertEquals(
            MessageSent("2022-04-21T20:56:22.393Z", expectedUsersFailedToSend),
            mapper.fromMlsDTO(
                SendMLSMessageResponse("2022-04-21T20:56:22.393Z",
                    emptyList(),
                    expectedUsersFailedToSend.map { it.toApi() })
            )
        )
    }

    companion object {
        private val RESULT_DTO = QualifiedSendMessageResponse.MessageSent(
            time = "2022-04-21T20:56:22.393Z",
            missing = mapOf(),
            redundant = mapOf(),
            deleted = mapOf(),
            failedToConfirmClients = mapOf(
                TEST_USER_ID.domain to mapOf(
                    TEST_USER_ID.value to listOf(TestClient.CLIENT_ID.value, ClientId("clientId12").value),
                    OTHER_USER_ID_2.value to listOf(
                        OTHER_USER_ID_2.value, ClientId("clientId21").value
                    )
                )
            )
        )
    }
}
