package com.wire.kalium.network.api.base.authenticated.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubconversationDeleteRequest(
    @SerialName("epoch") val epoch: ULong,
    @SerialName("group_id") val groupID: String
)
