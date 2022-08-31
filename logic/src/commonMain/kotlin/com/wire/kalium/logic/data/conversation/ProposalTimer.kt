package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import kotlinx.datetime.Instant

data class ProposalTimer(
    val groupID: GroupID,
    val timestamp: Instant
)
