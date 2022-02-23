package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId

sealed class SendMessageFailure: CoreFailure.FeatureFailure() {
    class ClientsHaveChanged(
        val missingClientsOfUsers: Map<UserId, List<ClientId>>,
        val redundantClientsOfUsers: Map<UserId, List<ClientId>>,
        val deletedClientsOfUsers: Map<UserId, List<ClientId>>
    ) : SendMessageFailure()
}
