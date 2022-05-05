package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId

data class ProteusSendMessageFailure(
    val missingClientsOfUsers: Map<UserId, List<ClientId>>,
    val redundantClientsOfUsers: Map<UserId, List<ClientId>>,
    val deletedClientsOfUsers: Map<UserId, List<ClientId>>
) : CoreFailure.FeatureFailure()
