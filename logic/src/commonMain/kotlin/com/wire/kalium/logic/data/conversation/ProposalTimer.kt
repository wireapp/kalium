package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.datetime.Instant

data class ProposalTimer(
    val groupID: String,
    val timestamp: Instant
)
