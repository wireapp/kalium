package com.wire.kalium.logic.data.conversation

import kotlinx.datetime.Instant

data class ProposalTimer(
    val groupID: String,
    val timestamp: Instant
)
