package com.wire.kalium.logic.data.message.mention

import com.wire.kalium.logic.data.user.UserId

data class MessageMention(
    val start: Int,
    val length: Int,
    val userId: UserId,
)
