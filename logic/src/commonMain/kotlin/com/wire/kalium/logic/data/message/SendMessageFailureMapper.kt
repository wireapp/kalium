package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.network.api.message.QualifiedUserIdToClientMap
import com.wire.kalium.network.exceptions.ProteusClientsChangedError

interface SendMessageFailureMapper {
    fun fromDTO(error: ProteusClientsChangedError): ProteusSendMessageFailure
}

class SendMessageFailureMapperImpl : SendMessageFailureMapper {
    override fun fromDTO(error: ProteusClientsChangedError): ProteusSendMessageFailure = with(error.errorBody) {
        ProteusSendMessageFailure(
            missing.fromNestedMapToSimpleMap(),
            redundant.fromNestedMapToSimpleMap(),
            deleted.fromNestedMapToSimpleMap()
        )
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
