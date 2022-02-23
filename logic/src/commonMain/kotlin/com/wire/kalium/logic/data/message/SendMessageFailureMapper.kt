package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.SendMessageFailure
import com.wire.kalium.network.api.message.QualifiedUserIdToClientMap
import com.wire.kalium.network.exceptions.QualifiedSendMessageError

interface SendMessageFailureMapper {
    fun fromDTO(error: QualifiedSendMessageError): SendMessageFailure
}

class SendMessageFailureMapperImpl : SendMessageFailureMapper {
    override fun fromDTO(error: QualifiedSendMessageError): SendMessageFailure {
        return if (error !is QualifiedSendMessageError.MissingDeviceError) {
            //TODO handle it better for no network or other cases, etc.
            SendMessageFailure.Unknown(error.cause)
        } else {
            val errorBody = error.errorBody
            SendMessageFailure.ClientsHaveChanged(
                errorBody.missing.fromNestedMapToSimpleMap(),
                errorBody.redundant.fromNestedMapToSimpleMap(),
                errorBody.deleted.fromNestedMapToSimpleMap()
            )
        }
    }

    private fun QualifiedUserIdToClientMap.fromNestedMapToSimpleMap(): Map<QualifiedID, List<ClientId>> {
        return this.entries.flatMap { domainEntry ->
            val domain = domainEntry.key
            val userEntries = domainEntry.value
            userEntries.map { userEntry ->
                val clients = userEntry.value.map { ClientId(it) }
                val userId = UserId(domain, userEntry.key)
                userId to clients
            }
        }.toMap()
    }
}
