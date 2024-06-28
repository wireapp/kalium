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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SendMessageFailureMapperTest {

    private val mapper = SendMessageFailureMapperImpl()

    @Test
    fun testFromDTOMapping() {
        assertEquals(RESULT, mapper.fromDTO(ERROR_DTO))
    }

    companion object {
        private val ERROR_DTO = ProteusClientsChangedError(
            errorBody = QualifiedSendMessageResponse.MissingDevicesResponse(
                time = Instant.DISTANT_PAST,
                missing = mapOf("missing_domain" to mapOf(userId(0) to listOf(clientId(0, 0), clientId(0, 1)))),
                redundant = mapOf("redundant_domain" to mapOf(userId(1) to listOf(clientId(1, 0), clientId(1, 1)))),
                deleted = mapOf(
                    "deleted_domain_0" to mapOf(userId(2) to listOf(clientId(2, 0), clientId(2, 1))),
                    "deleted_domain_1" to mapOf(userId(3) to listOf(clientId(3, 0), clientId(3, 1)))
                ),
                failedToConfirmClients = mapOf(
                    "failed_domain_0" to mapOf(userId(2) to listOf(clientId(2, 0), clientId(2, 1))),
                    "failed_domain_1" to mapOf(userId(3) to listOf(clientId(3, 0), clientId(3, 1)))
                )
            )
        )

        private val RESULT = ProteusSendMessageFailure(
            missingClientsOfUsers = mapOf(
                UserId(value = userId(0), "missing_domain") to listOf(
                    ClientId(clientId(0, 0)),
                    ClientId(clientId(0, 1))
                )
            ),
            redundantClientsOfUsers = mapOf(
                UserId(value = userId(1), "redundant_domain") to listOf(
                    ClientId(clientId(1, 0)),
                    ClientId(clientId(1, 1))
                )
            ),
            deletedClientsOfUsers = mapOf(
                UserId(value = userId(2), "deleted_domain_0") to listOf(
                    ClientId(clientId(2, 0)),
                    ClientId(clientId(2, 1))
                ),
                UserId(value = userId(3), "deleted_domain_1") to listOf(
                    ClientId(clientId(3, 0)),
                    ClientId(clientId(3, 1))
                ),

                ),
            failedClientsOfUsers = mapOf(
                UserId(value = userId(2), "failed_domain_0") to listOf(
                    ClientId(clientId(2, 0)),
                    ClientId(clientId(2, 1))
                ),
                UserId(value = userId(3), "failed_domain_1") to listOf(
                    ClientId(clientId(3, 0)),
                    ClientId(clientId(3, 1))
                ),

                )
        )

        private fun clientId(userN: Int = 0, clientN: Int = 0) = "user_${userN}_client_id_$clientN"
        private fun userId(n: Int = 0) = "user_${n}_id"
    }
}
