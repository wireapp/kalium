package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.data.message.UserSummary

data class MessageReaction(
    val emoji: String,
    val isSelfUser: Boolean,
    val userSummary: UserSummary
)
