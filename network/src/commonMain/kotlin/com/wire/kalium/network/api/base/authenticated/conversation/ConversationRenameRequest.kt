package com.wire.kalium.network.api.base.authenticated.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationRenameRequest(@SerialName("name") val name: String)
