package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.network.api.message.QualifiedSendMessageResponse
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
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
                time = "some_time",
                missing = mapOf("missing_domain" to mapOf(userId(0) to listOf(clientId(0, 0), clientId(0, 1)))),
                redundant = mapOf("redundant_domain" to mapOf(userId(1) to listOf(clientId(1, 0), clientId(1, 1)))),
                deleted = mapOf(
                    "deleted_domain_0" to mapOf(userId(2) to listOf(clientId(2, 0), clientId(2, 1))),
                    "deleted_domain_1" to mapOf(userId(3) to listOf(clientId(3, 0), clientId(3, 1)))
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

                )
        )

        private fun userId(n: Int = 0) = "user_${n}_id"
        private fun clientId(userN: Int = 0, clientN: Int = 0) = "user_${userN}_client_id_${clientN}"
    }
}
