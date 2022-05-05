package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedID

data class Participant(
    val id: QualifiedID,
    val clientId: String,
    val muted: Boolean
)
