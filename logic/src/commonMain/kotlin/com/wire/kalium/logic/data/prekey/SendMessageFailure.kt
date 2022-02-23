package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId

sealed class SendMessageFailure {
    object NetworkFailure : SendMessageFailure()
    class ClientsHaveChanged(
        val missingClientsOfUsers: Map<UserId, List<ClientId>>,
        val redundantClientsOfUsers: Map<UserId, List<ClientId>>,
        val deletedClientsOfUsers: Map<UserId, List<ClientId>>
    ) : SendMessageFailure()
}
